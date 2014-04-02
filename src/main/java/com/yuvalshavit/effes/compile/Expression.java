package com.yuvalshavit.effes.compile;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.antlr.v4.runtime.Token;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class Expression extends Node {
  private final EfType type;

  private Expression(Token token, EfType type) {
    super(token);
    this.type = type != null
      ? type
      : EfType.UNKNOWN;
  }

  public EfType resultType() {
    return type;
  }

  public static class UnrecognizedExpression extends Expression {

    public UnrecognizedExpression(Token token) {
      super(token, EfType.UNKNOWN);
    }

    @Override
    public void validate(CompileErrors errs) {
      // assume that whoever created this already entered the error
    }

    @Override
    public String toString() {
      return "unrecognized expression";
    }

    @Override
    public void state(NodeStateListener out) {
      // nothing
    }
  }

  public static class CaseExpression extends Expression {

    private final Expression matchAgainst;
    private final List<CaseAlternative> patterns;

    public CaseExpression(Token token, Expression matchAgainst, List<CaseAlternative> patterns) {
      super(token, computeType(patterns));
      this.matchAgainst = matchAgainst;
      this.patterns = ImmutableList.copyOf(patterns);
    }

    public Expression getMatchAgainst() {
      return matchAgainst;
    }

    public List<CaseAlternative> getPatterns() {
      return patterns;
    }

    @Override
    public void validate(CompileErrors errs) {
      // Validate that:
      // 1) each matcher *can* match the given expression
      // 2) at least one matcher *will* match the given expression
      // 3) each matcher is reachable (ie, the ones before it may not match)
      // 4) each matcher matches the right number of args
      matchAgainst.validate(errs);

      EfType matchType = matchAgainst.resultType();
      if (matchType instanceof EfType.DisjunctiveType) {
        EfType.DisjunctiveType dis = (EfType.DisjunctiveType) matchType;
        Set<EfType> matchAgainstTypes = dis.getAlternatives();
        Set<EfType> patternTypes = patterns
          .stream()
          .map(CaseAlternative::getType)
          .collect(Collectors.toSet());
        // extra types
        Sets.difference(patternTypes, matchAgainstTypes).forEach(t -> errs.add(
          matchAgainst.token(),
          String.format("pattern type (%s) can never match expression type (%s)", t, matchType)));
        // missing types
        Sets.difference(matchAgainstTypes, patternTypes).forEach(t -> errs.add(
          matchAgainst.token(),
          "expression alternative is never matched: " + t));
        patterns.forEach(alt -> {
          int expected = alt.getType().getArgs().size();
          int actual = alt.getBindings().size();
          if (expected != actual) {
            String plural = expected == 1 ? "" : "s";
            errs.add(token(), String.format("expected %d binding%s but found %d", expected, plural, actual));
          }
        });
      } else {
        errs.add(token(), "case requires a disjunctive type (found " + matchType + ")");
      }
    }

    @Override
    public void state(NodeStateListener out) {
      out.child("case", matchAgainst);
      patterns.forEach(p -> out.child("of " + p.getType(), p.getIfMatchedExpression()));
    }

    @Override
    public String toString() {
      return String.format("case (%s) of...", matchAgainst);
    }

    private static EfType computeType(List<CaseAlternative> patterns) {
      EfType result = null;
      for (CaseAlternative p : patterns) {
        EfType patternResult = p.getIfMatchedExpression().resultType();
        result = result != null
          ? EfType.disjunction(result, patternResult)
          : patternResult;
      }
      return result;
    }

    public static class CaseAlternative {
      private final EfType.SimpleType type;
      private final Expression ifMatched;
      private final List<EfVar> bindings;

      public CaseAlternative(EfType.SimpleType type, List<EfVar> bindings, Expression ifMatched) {
        this.type = type;
        this.ifMatched = ifMatched;
        this.bindings = ImmutableList.copyOf(bindings);
      }

      public Expression getIfMatchedExpression() {
        return ifMatched;
      }

      public EfType.SimpleType getType() {
        return type;
      }

      public List<EfVar> getBindings() {
        return bindings;
      }
    }
  }

  public static class CtorInvoke extends Expression {

    @Nullable
    private final EfType.SimpleType simpleType;
    private final List<Expression> args;

    public CtorInvoke(Token token, @Nullable EfType.SimpleType type, List<Expression> args) {
      super(token, type);
      simpleType = type;
      this.args = ImmutableList.copyOf(args);
    }

    public List<Expression> getArgs() {
      return args;
    }

    @Override
    public void validate(CompileErrors errs) {
      if (simpleType != null) {
        // if it's null, the call site that created this object should lodge the error; it has the missing type's name
        int expected = simpleType.getArgs().size();
        int actual = getArgs().size();
        if (expected != actual) {
          String plural = expected == 1 ? "" : "s";
          errs.add(token(), String.format("expected %d argument%s to %s, but found %d",
            expected, plural, simpleType, actual));
        }
        getArgs().forEach(a -> a.validate(errs));
      }
    }

    @Override
    public void state(NodeStateListener out) {
      out.scalar("type", simpleType);
    }

    @Override
    public String toString() {
      return resultType().toString();
    }

    @Nullable
    public EfType.SimpleType simpleType() {
      return simpleType;
    }
  }

  public static class MethodInvoke extends Expression {
    private final String methodName;
    private final List<Expression> args;
    private final boolean isBuiltIn;
    private final boolean usedAsExpression;

    public MethodInvoke(Token token, String methodName, List<Expression> args, EfType resultType, boolean isBuiltin,
                        boolean usedAsExpression) {
      super(token, resultType);
      this.methodName = methodName;
      this.args = args;
      this.isBuiltIn = isBuiltin;
      this.usedAsExpression = usedAsExpression;
    }

    public boolean isBuiltIn() {
      return isBuiltIn;
    }

    public String getMethodName() {
      return methodName;
    }

    public List<Expression> getArgs() {
      return args;
    }

    @Override
    public void validate(CompileErrors errs) {
      args.forEach(arg -> arg.validate(errs));
      if (usedAsExpression && EfType.VOID.equals(resultType())) {
        errs.add(token(), String.format("method '%s' has no return value", methodName));
      }
    }

    @Override
    public void state(NodeStateListener out) {
      String name = methodName;
      if (isBuiltIn()) {
        name += " [built-in]";
      }
      out.scalar("method", name);
      for (int i = 0; i < args.size(); ++i) {
        out.child("arg" + i, args.get(i));
      }
    }

    @Override
    public String toString() {
      return String.format("%s(%s)", methodName, Joiner.on(", ").join(args));
    }
  }

  public static class VarExpression extends Expression {
    private final String name;
    private final int pos;
    private final boolean isArg;

    public VarExpression(Token token, EfVar var) {
      super(token, var.getType());
      this.name = var.getName();
      this.pos = var.getArgPosition();
      this.isArg = var.isArg();
    }

    @Override
    public void validate(CompileErrors errs) {
      // nothing to do
    }

    @Override
    public void state(NodeStateListener out) {
      String label = isArg
        ? "arg"
        : "var";
      out.scalar(label, name);
      out.scalar("pos", pos);
    }

    public int pos() {
      return pos;
    }

    public boolean isArg() {
      return isArg;
    }

    @Override
    public String toString() {
      return name;
    }
  }
}

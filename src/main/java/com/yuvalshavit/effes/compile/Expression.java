package com.yuvalshavit.effes.compile;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import org.antlr.v4.runtime.Token;

import javax.annotation.Nullable;
import java.util.List;

public abstract class Expression extends Node {
  private Expression(Token token, EfType type) {
    super(token, type != null ? type : EfType.UNKNOWN);
  }

  public static Expression thisExpression(Token token, EfType.SimpleType declaringType) {
    EfVar arg0 = EfVar.arg("$this", 0, declaringType);
    return new VarExpression(token, arg0);
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

  /**
   * An inline assign-and-return expression. This is how all expressions work in Java (and C, etc), but not in Effes.
   * However, there are times we need this construct internally.
   */
  public static class AssignExpression extends Expression {
    private final Expression delegate;
    private final EfVar assignTo;

    public AssignExpression(Expression delegate, EfVar assignTo) {
      super(delegate.token(), delegate.resultType());
      this.delegate = delegate;
      this.assignTo = assignTo;
    }

    @Nullable
    @Override
    public EfVar var() {
      return assignTo;
    }

    @Override
    public String toString() {
      return String.format("%s = %s", assignTo.getName(), delegate);
    }

    @Override
    public void validate(CompileErrors errs) {
      delegate.validate(errs);
    }

    @Override
    public void state(NodeStateListener out) {
      out.scalar("assignTo", assignTo);
      out.child(delegate);
    }

    public Expression getDelegate() {
      return delegate;
    }

    public EfVar getVar() {
      return assignTo;
    }
  }

  public static class CaseExpression extends Expression {
    private final CaseConstruct<Expression> delegate;

    public CaseExpression(Token token, CaseConstruct<Expression> delegate) {
      super(token, delegate.resultType());
      this.delegate = delegate;
    }

    @Override
    public String toString() {
      return delegate.toString();
    }

    @Override
    public void validate(CompileErrors errs) {
      delegate.validate(token(), errs);
    }

    @Override
    public void state(NodeStateListener out) {
      delegate.state(out);
    }

    public CaseConstruct<Expression> construct() {
      return delegate;
    }
  }

  public static class CastExpression extends Expression {
    private final Expression delegate;

    public CastExpression(Expression delegate, EfType castTo) {
      super(delegate.token(), castTo);
      this.delegate = delegate;
    }

    public Expression getDelegate() {
      return delegate;
    }

    @Override
    public String toString() {
      return String.format("(%s) %s", resultType(), delegate);
    }

    @Override
    public void validate(CompileErrors errs) {
      delegate.validate(errs);
      if (!delegate.resultType().contains(resultType())) {
        errs.add(token(), String.format("cannot cast '%s' to '%s' (possibly a compiler error)",
                                        delegate.resultType(), resultType()));
      }
    }

    @Override
    public void state(NodeStateListener out) {
      out.scalar("castTo", resultType());
      out.child(delegate);
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

  public static class InstanceArg extends Expression {
    private final Expression target;
    private final EfVar arg;

    public InstanceArg(Token token, Expression target, EfVar arg) {
      super(token, arg.getType());
      this.target = target;
      this.arg = arg;
    }

    public Expression getTarget() {
      return target;
    }

    public EfVar getArg() {
      return arg;
    }

    @Override
    public String toString() {
      return String.format("(%s) %s", target, arg);
    }

    @Override
    public void validate(CompileErrors errs) {
      // nothing to do
    }

    @Override
    public void state(NodeStateListener out) {
      out.child("target", target);
      out.scalar("arg", arg);
    }
  }

  public static class MethodInvoke extends Expression {
    private final MethodId methodId;
    private final List<Expression> args;
    private final boolean isBuiltIn;
    private final boolean usedAsExpression;

    public MethodInvoke(Token token, MethodId methodId, List<Expression> args, EfType resultType,
                        boolean isBuiltin, boolean usedAsExpression) {
      super(token, resultType);
      this.methodId = methodId;
      this.args = args;
      this.isBuiltIn = isBuiltin;
      this.usedAsExpression = usedAsExpression;
    }

    public boolean isBuiltIn() {
      return isBuiltIn;
    }

    public MethodId getMethodId() {
      return methodId;
    }

    public List<Expression> getArgs() {
      return args;
    }

    @Override
    public void validate(CompileErrors errs) {
      args.forEach(arg -> arg.validate(errs));
      if (usedAsExpression && EfType.VOID.equals(resultType())) {
        errs.add(token(), String.format("method '%s' has no return value", methodId));
      }
    }

    @Override
    public void state(NodeStateListener out) {
      String name = methodId.toString();
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
      return String.format("%s(%s)", methodId, Joiner.on(", ").join(args));
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

    public EfVar getVar() {
      return EfVar.create(isArg(), name(), pos(), resultType());
    }

    public String name() {
      return name;
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

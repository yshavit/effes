package com.yuvalshavit.effes.compile.node;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.yuvalshavit.effes.compile.NodeStateVisitor;
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
    private final List<Expression> children;

    public UnrecognizedExpression(Token token, List<Expression> children) {
      super(token, EfType.UNKNOWN);
      this.children = ImmutableList.copyOf(children);
    }

    public UnrecognizedExpression(Token token) {
      this(token, ImmutableList.of());
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
    public void state(NodeStateVisitor out) {
      children.forEach(c -> c.state(out));
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
    }

    @Override
    public void state(NodeStateVisitor out) {
      out.visitScalar("assignTo", assignTo);
      out.visitChild(delegate);
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
    public void state(NodeStateVisitor out) {
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
      if (!delegate.resultType().contains(resultType())) {
        errs.add(token(), String.format("cannot cast '%s' to '%s' (possibly a compiler error)",
                                        delegate.resultType(), resultType()));
      }
    }

    @Override
    public void state(NodeStateVisitor out) {
      out.visitScalar("castTo", resultType());
      out.visitChild(delegate);
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
        for (int i = 0, len = Math.min(simpleType.getArgs().size(), args.size()); i < len; ++i) {
          Expression actualArg = args.get(i);
          EfType actualType = actualArg.resultType();
          EfType expectedType = simpleType.getArgs().get(i).getType();
          if (!expectedType.contains(actualType)) {
            errs.add(actualArg.token(), String.format("expected type %s but found %s", expectedType, actualType));
          }
        }
      }
    }

    @Override
    public void state(NodeStateVisitor out) {
      out.visitScalar("type", simpleType);
      for (int i = 0; i < args.size(); ++i) {
        out.visitChild("arg" + i, args.get(i));
      }
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
    public void state(NodeStateVisitor out) {
      out.visitChild("target", target);
      out.visitScalar("arg", arg);
    }
  }

  public static class MethodInvoke extends Expression {
    private final MethodId methodId;
    private final EfMethod<?> method;
    private final List<Expression> args;
    private final boolean isBuiltIn;
    private final boolean usedAsExpression;

    public MethodInvoke(Token token,
                        MethodId methodId,
                        EfMethod<?> method,
                        List<Expression> args,
                        boolean isBuiltin, boolean usedAsExpression) {
      super(token, method.getResultType());
      this.methodId = methodId;
      this.method = method;
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
      if (usedAsExpression && EfType.VOID.equals(resultType())) {
        errs.add(token(), String.format("method '%s' has no return value", methodId));
      }
      // validate arg types
      List<EfType> expectedArgs = method.getArgs().viewTypes();
      for (int i = 0, len = Math.min(args.size(), expectedArgs.size()); i < len; ++i) {
        EfType invokeArg = args.get(i).resultType();
        EfType expectedArg = expectedArgs.get(i);
        // e.g. method (True | False), arg is True
        if (!expectedArg.contains(invokeArg)) {
          errs.add(
            args.get(i).token(),
            String.format("mismatched types for '%s': expected %s but found %s", methodId, expectedArg, invokeArg));
        }
      }
      // validate arity
      int nExplicitInvokeArgs = args.size();
      int nExplicitExpectedArgs = expectedArgs.size();
      if (methodId.getDefinedOn() != null) {
        --nExplicitExpectedArgs;
        --nExplicitInvokeArgs;
      }
      if (nExplicitExpectedArgs != nExplicitInvokeArgs) {
        String plural = nExplicitExpectedArgs == 1 ? "" : "s";
        errs.add(token(), String.format("for method %s, expected %d argument%s but found %d",
                                         methodId, nExplicitExpectedArgs, plural, nExplicitInvokeArgs));
      }
    }

    @Override
    public void state(NodeStateVisitor out) {
      String name = methodId.toString();
      if (isBuiltIn()) {
        name += " [built-in]";
      }
      out.visitScalar("method", name);
      for (int i = 0; i < args.size(); ++i) {
        out.visitChild("arg" + i, args.get(i));
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
    public void state(NodeStateVisitor out) {
      String label = isArg
        ? "arg"
        : "var";
      out.visitScalar(label, name);
      out.visitScalar("pos", pos);
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

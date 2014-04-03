package com.yuvalshavit.effes.compile;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import org.antlr.v4.runtime.Token;

import javax.annotation.Nullable;
import java.util.List;

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
    private final CaseConstruct delegate;

    public CaseExpression(Token token, CaseConstruct delegate) {
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

    public CaseConstruct construct() {
      return delegate;
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

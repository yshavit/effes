package com.yuvalshavit.effes.compile;

import com.google.common.base.Joiner;

import java.util.List;

public abstract class Statement extends StatefulObject {
  private Statement() {}

  public static class ReturnStatement extends Statement {
    private final Expression expression;

    public ReturnStatement(Expression expression) {
      this.expression = expression;
    }

    public Expression getExpression() {
      return expression;
    }

    @Override
    public String toString() {
      return "return " + expression;
    }

    @Override
    protected Object[] state() {
      return new Object[] { expression };
    }
  }

  public static class MethodInvoke extends Statement {
    private final SimpleType resultType;
    private final String methodName;
    private final List<Expression> args;

    public MethodInvoke(String methodName, List<Expression> args, SimpleType resultType) {
      this.methodName = methodName;
      this.args = args;
      this.resultType = resultType;
    }

    public SimpleType getResultType() {
      return resultType;
    }

    public String getMethodName() {
      return methodName;
    }

    public List<Expression> getArgs() {
      return args;
    }

    @Override
    protected Object[] state() {
      return new Object[] { methodName, args };
    }

    @Override
    public String toString() {
      return String.format("%s(%s)", methodName, Joiner.on(", ").join(args));
    }
  }
}

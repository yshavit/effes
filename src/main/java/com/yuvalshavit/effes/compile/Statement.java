package com.yuvalshavit.effes.compile;

import com.google.common.base.Joiner;
import org.antlr.v4.runtime.Token;

import java.util.List;

public abstract class Statement extends StatefulObject {
  private final Token token;

  private Statement(Token token) {
    this.token = token;
  }

  public Token token() {
    return token;
  }

  public static class ReturnStatement extends Statement {
    private final Expression expression;

    public ReturnStatement(Token token, Expression expression) {
      super(token);
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
    private final EfType resultType;
    private final String methodName;
    private final List<Expression> args;
    private final boolean isBuiltIn;

    public MethodInvoke(Token token, String name, List<Expression> args, EfType resultType, boolean isBuiltIn) {
      super(token);
      this.methodName = name;
      this.args = args;
      this.resultType = resultType;
      this.isBuiltIn = isBuiltIn;
    }

    public boolean isBuiltIn() {
      return isBuiltIn;
    }

    public EfType getResultType() {
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

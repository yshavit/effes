package com.yuvalshavit.effes.compile;

import org.antlr.v4.runtime.Token;

import javax.annotation.Nonnull;
import java.util.List;

public abstract class Expression extends StatefulObject {
  private final Token token;
  private final EfType.SimpleType type;

  private Expression(Token token, EfType.SimpleType type) {
    this.token = token;
    this.type = type;
  }

  public final EfType.SimpleType resultType() {
    return type;
  }

  public Token token() {
    return token;
  }

  public static class CtorInvoke extends Expression {

    public CtorInvoke(Token token, @Nonnull EfType.SimpleType type) {
      super(token, type);
    }

    @Override
    public String toString() {
      return String.format("%s()", resultType());
    }

    @Override
    protected Object[] state() {
      return new Object[] { resultType() };
    }
  }

  public static class MethodInvoke extends Expression {
    private final String methodName;
    private final EfMethod<?> method;
    private final List<Expression> args;

    public MethodInvoke(Token token, String methodName, EfMethod<?> method, List<Expression> args) {
      super(token, method.getResultType());
      this.methodName = methodName;
      this.method = method;
      this.args = args;
    }

    public String getMethodName() {
      return methodName;
    }

    public List<Expression> getArgs() {
      return args;
    }

    @Override
    protected Object[] state() {
      return new Object[] {methodName, method, args, resultType() };
    }

    @Override
    public String toString() {
      return methodName + method;
    }
  }
}

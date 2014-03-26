package com.yuvalshavit.effes.compile;

import javax.annotation.Nonnull;
import java.util.List;

public abstract class Expression extends StatefulObject {
  private final SimpleType type;

  private Expression(SimpleType type) {
    this.type = type;
  }

  public final SimpleType resultType() {
    return type;
  }

  public static class CtorInvoke extends Expression {

    public CtorInvoke(@Nonnull SimpleType type) {
      super(type);
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

    public MethodInvoke(String methodName, EfMethod<?> method, List<Expression> args) {
      super(method.getResultType());
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

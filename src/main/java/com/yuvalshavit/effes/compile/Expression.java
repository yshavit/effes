package com.yuvalshavit.effes.compile;

import com.google.common.base.Joiner;
import org.antlr.v4.runtime.Token;

import javax.annotation.Nullable;
import java.util.List;

public abstract class Expression extends StatefulObject {
  private final Token token;
  private final EfType type;

  private Expression(Token token, EfType type) {
    this.token = token;
    this.type = type != null
      ? type
      : EfType.UNKNOWN;
  }

  public EfType resultType() {
    return type;
  }

  public Token token() {
    return token;
  }

  public static class CtorInvoke extends Expression {

    @Nullable
    private final EfType.SimpleType simpleType;

    public CtorInvoke(Token token, @Nullable EfType.SimpleType type) {
      super(token, type);
      simpleType = type;
    }

    @Override
    public String toString() {
      return resultType().toString();
    }

    @Nullable
    public EfType.SimpleType simpleType() {
      return simpleType;
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

    public MethodInvoke(Token token, String methodName, EfMethod<?> method, List<Expression> args, EfType resultType) {
      super(token, resultType);
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
      return String.format("%s(%s)", methodName, Joiner.on(", ").join(args));
    }
  }
}

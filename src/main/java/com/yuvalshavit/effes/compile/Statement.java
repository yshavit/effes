package com.yuvalshavit.effes.compile;

import com.google.common.base.Joiner;
import org.antlr.v4.runtime.Token;

import java.util.List;

public abstract class Statement extends Node {
  private final Token token;

  private Statement(Token token) {
    this.token = token;
  }

  public Token token() {
    return token;
  }

  public static class AssignStatement extends Statement {
    private final EfVar var;
    private final Expression value;

    public AssignStatement(Token token, EfVar var, Expression value) {
      super(token);
      this.var = var;
      this.value = value;
    }

    public EfVar var() {
      return var;
    }

    public Expression value() {
      return value;
    }

    @Override
    public String toString() {
      return var + " = " + value;
    }

    @Override
    public void validate(CompileErrors errs) {
      value.validate(errs);
    }
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
    public void validate(CompileErrors errs) {
      expression.validate(errs);
    }

    @Override
    public String toString() {
      return "return " + expression;
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
    public void validate(CompileErrors errs) {
      args.forEach(arg -> arg.validate(errs));
    }

    @Override
    public String toString() {
      return String.format("%s(%s)", methodName, Joiner.on(", ").join(args));
    }
  }
}

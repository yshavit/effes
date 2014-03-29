package com.yuvalshavit.effes.compile;

import org.antlr.v4.runtime.Token;

public abstract class Statement extends Node {

  private Statement(Token token) {
    super(token);
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

    @Override
    public void state(NodeStateListener out) {
      out.child(var, value);
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
    public void state(NodeStateListener out) {
      out.child(expression);
    }

    @Override
    public String toString() {
      return "return " + expression;
    }
  }

  public static class MethodInvoke extends Statement {
    private final Expression.MethodInvoke methodExpr;

    public MethodInvoke(Expression.MethodInvoke methodExpr) {
      super(methodExpr.token());
      this.methodExpr = methodExpr;
    }

    public Expression.MethodInvoke methodExpr() {
      return methodExpr;
    }

    @Override
    public void validate(CompileErrors errs) {
      methodExpr.validate(errs);
    }

    @Override
    public void state(NodeStateListener out) {
      out.child("method", methodExpr);
    }

    @Override
    public String toString() {
      return methodExpr.toString();
    }
  }
}

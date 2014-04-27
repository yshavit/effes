package com.yuvalshavit.effes.compile;

import org.antlr.v4.runtime.Token;

import javax.annotation.Nonnull;

public abstract class Statement extends Node {

  private Statement(Token token, @Nonnull EfType resultType) {
    super(token, resultType);
  }

  public static class AssignStatement extends Statement {
    private final EfVar var;
    private final Expression value;

    public AssignStatement(Token token, EfVar var, Expression value) {
      super(token, EfType.VOID);
      this.var = var;
      this.value = value;
    }

    @Nonnull
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
    }

    @Override
    public void state(NodeStateVisitor out) {
      out.visitScalar("var", var);
      out.visitChild("val", value);
    }
  }

  public static class CaseStatement extends Statement {
    private final CaseConstruct<Block> delegate;

    public CaseStatement(Token token, CaseConstruct<Block> delegate) {
      super(token, delegate.resultType());
      this.delegate = delegate;
    }

    public CaseConstruct<Block> construct() {
      return delegate;
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
  }

  public static class ReturnStatement extends Statement {
    private final Expression expression;

    public ReturnStatement(Token token, Expression expression) {
      super(token, expression.resultType());
      this.expression = expression;
    }

    public Expression getExpression() {
      return expression;
    }

    @Override
    public void validate(CompileErrors errs) {
    }

    @Override
    public void state(NodeStateVisitor out) {
      out.visitChild(expression);
    }

    @Override
    public String toString() {
      return "return " + expression;
    }
  }

  public static class MethodInvoke extends Statement {
    private final Expression methodExpr;

    public MethodInvoke(Expression methodExpr) {
      super(methodExpr.token(), EfType.VOID);
      this.methodExpr = methodExpr;
    }

    public Expression methodExpr() {
      return methodExpr;
    }

    @Override
    public void validate(CompileErrors errs) {
    }

    @Override
    public void state(NodeStateVisitor out) {
      out.visitChild(methodExpr);
    }

    @Override
    public boolean elideFromState() {
      return true; // we only its child, the expression invoke
    }

    @Override
    public String toString() {
      return methodExpr.toString();
    }
  }

  public static class UnrecognizedStatement extends Statement {

    public UnrecognizedStatement(Token token) {
      super(token, EfType.VOID);
    }

    @Override
    public String toString() {
      return "<unrecognized statement>";
    }

    @Override
    public void validate(CompileErrors errs) {
      errs.add(token(), "unrecognized statement");
    }

    @Override
    public void state(NodeStateVisitor out) {
      throw new UnsupportedOperationException(); // TODO
    }
  }
}

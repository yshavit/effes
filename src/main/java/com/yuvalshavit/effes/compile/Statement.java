package com.yuvalshavit.effes.compile;

public abstract class Statement extends StatefulObject {
  private Statement() {}

  public static class ReturnStatement extends Statement {
    private final Expression expression;

    public ReturnStatement(Expression expression) {
      this.expression = expression;
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
}

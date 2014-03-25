package com.yuvalshavit.effes.interpreter;

import com.yuvalshavit.effes.compile.Statement;

public abstract class ExecutableStatement implements ExecutableElement {
  private final Statement source;

  protected ExecutableStatement(Statement source) {
    this.source = source;
  }

  public static class ReturnStatement extends ExecutableStatement {
    private final ExecutableExpression value;

    public ReturnStatement(Statement.ReturnStatement source, ExecutableExpressionCompiler expressionCompiler) {
      super(source);
      this.value = expressionCompiler.apply(source.getExpression());
    }

    @Override
    public void execute(StateStack stack) {
      value.execute(stack); // just leave it on the stack
    }
  }
}

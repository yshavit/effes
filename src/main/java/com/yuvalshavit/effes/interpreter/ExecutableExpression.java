package com.yuvalshavit.effes.interpreter;

import com.yuvalshavit.effes.compile.Expression;
import com.yuvalshavit.effes.compile.SimpleType;

public abstract class ExecutableExpression implements ExecutableElement {
  private final Expression source;

  private ExecutableExpression(Expression source) {
    this.source = source;
  }

  @Override
  public String toString() {
    return source.toString();
  }

  public static class CtorExpression extends ExecutableExpression {
    private final SimpleType type;

    public CtorExpression(Expression.CtorInvoke source) {
      super(source);
      type = source.resultType();
    }

    @Override
    public void execute(StateStack stack) {
      stack.push(type);
    }
  }
}
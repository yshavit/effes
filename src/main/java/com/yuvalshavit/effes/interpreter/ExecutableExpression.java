package com.yuvalshavit.effes.interpreter;

import com.yuvalshavit.effes.compile.EfType;
import com.yuvalshavit.effes.compile.Expression;

import java.util.List;

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
    private final EfType.SimpleType ctorType;

    public CtorExpression(Expression.CtorInvoke source) {
      super(source);
      ctorType = source.simpleType();
    }

    @Override
    public void execute(StateStack stack) {
      stack.push(ctorType);
    }
  }

  public static class MethodInvokeExpression extends ExecutableExpression {
    private final List<ExecutableExpression> args;
    private final ExecutableElement body;

    public MethodInvokeExpression(Expression.MethodInvoke source, List<ExecutableExpression> args, ExecutableElement body) {
      super(source);
      this.args = args;
      this.body = body;
    }

    @Override
    public void execute(StateStack stack) {
      Object rv = ExecutableStatement.MethodInvoke.invoke(body, args, stack);
      stack.push(rv);
    }
  }
}

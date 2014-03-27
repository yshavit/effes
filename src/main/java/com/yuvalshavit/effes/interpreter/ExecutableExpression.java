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
      invoke(body, args, stack);
    }

    public static void invoke(ExecutableElement body, List<ExecutableExpression> args, StateStack stack) {
      stack.openFrame(args);
      body.execute(stack);
      stack.closeFrame();
    }
  }

  public static class ArgReadExpression extends ExecutableExpression {
    private final int pos;
    public ArgReadExpression(Expression.ArgExpression source) {
      super(source);
      this.pos = source.pos();
    }

    @Override
    public void execute(StateStack stack) {
      stack.pushArgToStack(pos);
    }
  }
}

package com.yuvalshavit.effes.interpreter;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.yuvalshavit.effes.compile.Statement;

import java.util.List;
import java.util.function.Supplier;

public abstract class ExecutableStatement implements ExecutableElement {
  private final Statement source;

  protected ExecutableStatement(Statement source) {
    this.source = source;
  }

  @Override
  public String toString() {
    return source.toString();
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

  public static class MethodInvoke extends ExecutableStatement {
    private final List<ExecutableExpression> args;
    private final Supplier<ExecutableElement> body;

    public MethodInvoke(Statement.MethodInvoke source,
                        ExecutableExpressionCompiler expressionCompiler,
                        Supplier<ExecutableElement> body)
    {
      super(source);
      this.args = ImmutableList.copyOf(Lists.transform(source.getArgs(), expressionCompiler::apply));
      this.body = body;
    }

    @Override
    public void execute(StateStack stack) {
      invoke(body.get(), args, stack);
      stack.pop(); // get rid of the return value
    }

    public static void invoke(ExecutableElement body, List<ExecutableExpression> args, StateStack stack) {
      // TODO this should actually be in ExecutableExpression
      stack.openFrame(args);
      body.execute(stack);
      stack.closeFrame();
    }
  }
}

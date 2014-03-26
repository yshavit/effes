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
    }

    public static Object invoke(ExecutableElement body, List<ExecutableExpression> args, StateStack stack) {
      for (ExecutableExpression arg : args) {
        arg.execute(stack);
      }
      body.execute(stack);
      // TODO not efficient, but we can work on that later!
      Object rv = stack.pop();
      for (int i = 0, nArgs = args.size(); i < nArgs; ++ i) {
        stack.pop(); // TODO also not efficient!
      }
      return rv; // will be useful later, when this is used in an expression
    }
  }
}

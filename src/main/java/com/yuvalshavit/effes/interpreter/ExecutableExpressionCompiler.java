package com.yuvalshavit.effes.interpreter;

import com.yuvalshavit.effes.compile.Expression;
import com.yuvalshavit.util.Dispatcher;

import java.util.function.Function;

public final class ExecutableExpressionCompiler implements Function<Expression, ExecutableExpression> {
  @Override
  public ExecutableExpression apply(Expression expression) {
    return ExecutableExpressionCompiler.dispatcher.apply(this, expression);
  }

  private static final Dispatcher<ExecutableExpressionCompiler, Expression, ExecutableExpression> dispatcher =
    Dispatcher.builder(ExecutableExpressionCompiler.class, Expression.class, ExecutableExpression.class)
      .put(Expression.CtorInvoke.class, ExecutableExpressionCompiler::ctorInvoke)
      .build();

  private ExecutableExpression ctorInvoke(Expression.CtorInvoke expr) {
    return new ExecutableExpression.CtorExpression(expr);
  }
}

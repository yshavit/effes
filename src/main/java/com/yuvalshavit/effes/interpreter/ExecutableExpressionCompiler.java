package com.yuvalshavit.effes.interpreter;

import com.google.common.annotations.VisibleForTesting;
import com.yuvalshavit.effes.compile.Expression;
import com.yuvalshavit.util.Dispatcher;

import java.util.function.Function;

public final class ExecutableExpressionCompiler implements Function<Expression, ExecutableExpression> {
  @Override
  public ExecutableExpression apply(Expression expression) {
    return ExpressionDispatcher.dispatcher.apply(new ExpressionDispatcher(), expression);
  }

  @VisibleForTesting
  static class ExpressionDispatcher {
    static final Dispatcher<ExpressionDispatcher, Expression, ExecutableExpression> dispatcher =
      Dispatcher.builder(ExpressionDispatcher.class, Expression.class, ExecutableExpression.class)
        .put(Expression.CtorInvoke.class, ExpressionDispatcher::ctorInvoke)
        .build();

    public ExecutableExpression ctorInvoke(Expression.CtorInvoke expr) {
      return new ExecutableExpression.CtorExpression(expr);
    }
  }
}

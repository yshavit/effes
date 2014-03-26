package com.yuvalshavit.effes.interpreter;

import com.yuvalshavit.effes.compile.EfMethod;
import com.yuvalshavit.effes.compile.Expression;
import com.yuvalshavit.effes.compile.MethodsRegistry;
import com.yuvalshavit.util.Dispatcher;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ExecutableExpressionCompiler implements Function<Expression, ExecutableExpression> {

  private final MethodsRegistry<ExecutableElement> methods;

  public ExecutableExpressionCompiler(MethodsRegistry<ExecutableElement> methods) {
    this.methods = methods;
  }

  @Override
  public ExecutableExpression apply(Expression expression) {
    return dispatcher.apply(this, expression);
  }

  private static final Dispatcher<ExecutableExpressionCompiler, Expression, ExecutableExpression> dispatcher =
    Dispatcher.builder(ExecutableExpressionCompiler.class, Expression.class, ExecutableExpression.class)
      .put(Expression.MethodInvoke.class, ExecutableExpressionCompiler::methodInvoke)
      .put(Expression.CtorInvoke.class, ExecutableExpressionCompiler::ctorInvoke)
      .build();

  private ExecutableExpression ctorInvoke(Expression.CtorInvoke expr) {
    return new ExecutableExpression.CtorExpression(expr);
  }

  private ExecutableExpression methodInvoke(Expression.MethodInvoke expr) {
    List<ExecutableExpression> args = expr.getArgs().stream().map(this::apply).collect(Collectors.toList());
    EfMethod<? extends ExecutableElement> method = methods.getMethod(expr.getMethodName());
    assert method != null;
    return new ExecutableExpression.MethodInvokeExpression(expr, args, method.getBody());
  }
}

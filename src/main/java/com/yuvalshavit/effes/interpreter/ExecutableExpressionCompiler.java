package com.yuvalshavit.effes.interpreter;

import com.yuvalshavit.effes.compile.node.Expression;
import com.yuvalshavit.effes.compile.node.MethodId;
import com.yuvalshavit.util.Dispatcher;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class ExecutableExpressionCompiler implements Function<Expression, ExecutableExpression> {

  private final Function<MethodId, ExecutableMethod> methods;
  private final Function<MethodId, ExecutableMethod> builtInMethods;

  public ExecutableExpressionCompiler(Function<MethodId, ExecutableMethod> methods,
                                      Function<MethodId, ExecutableMethod> builtInMethods) {
    this.methods = methods;
    this.builtInMethods = builtInMethods;
  }

  @Override
  public ExecutableExpression apply(Expression expression) {
    return dispatcher.apply(this, expression);
  }

  private static final Dispatcher<ExecutableExpressionCompiler, Expression, ExecutableExpression> dispatcher =
    Dispatcher.builder(ExecutableExpressionCompiler.class, Expression.class, ExecutableExpression.class)
      .put(Expression.AssignExpression.class, ExecutableExpressionCompiler::assignExpr)
      .put(Expression.CaseExpression.class, ExecutableExpressionCompiler::caseExpr)
      .put(Expression.InstanceArg.class, ExecutableExpressionCompiler::instanceArg)
      .put(Expression.IntLiteral.class, ExecutableExpressionCompiler::intLiteral)
      .put(Expression.MethodInvoke.class, ExecutableExpressionCompiler::methodInvoke)
      .put(Expression.CastExpression.class, ExecutableExpressionCompiler::castExpr)
      .put(Expression.CtorInvoke.class, ExecutableExpressionCompiler::ctorInvoke)
      .put(Expression.VarExpression.class, ExecutableExpressionCompiler::varExpr)
      .put(Expression.UnrecognizedExpression.class, ExecutableExpressionCompiler::unrecognizedExpr)
      .build((me, e) -> {
        throw new AssertionError(e);
      });

  private ExecutableExpression assignExpr(Expression.AssignExpression expr) {
    return new ExecutableExpression.AssignExpression(expr, apply(expr.getDelegate()));
  }

  private ExecutableExpression caseExpr(Expression.CaseExpression expr) {
    ExecutableExpression matchAgainst = apply(expr.construct().getMatchAgainst());
    List<ExecutableCase.CaseMatcher> matchers = expr.construct().getPatterns().stream().map(p -> {
      ExecutableExpression ifMatch = apply(p.getIfMatched());
      return new ExecutableCase.CaseMatcher(p.getType(), ifMatch);
    }).collect(Collectors.toList());
    return new ExecutableExpression.CaseExpression(expr, matchAgainst, matchers);
  }

  private ExecutableExpression castExpr(Expression.CastExpression expr) {
    return new ExecutableExpression.CastExpression(expr, apply(expr.getDelegate()));
  }

  private ExecutableExpression instanceArg(Expression.InstanceArg expr) {
    ExecutableExpression target = apply(expr.getTarget());
    return new ExecutableExpression.InstanceArg(expr, target, expr.getArg());
  }
  
  private ExecutableExpression intLiteral(Expression.IntLiteral expr) {
    return new ExecutableExpression.IntLiteral(expr);
  }

  private ExecutableExpression ctorInvoke(Expression.CtorInvoke expr) {
    List<ExecutableExpression> args = expr.getArgs().stream().map(this::apply).collect(Collectors.toList());
    return new ExecutableExpression.CtorExpression(expr, args);
  }

  public ExecutableExpression.MethodInvokeExpression methodInvoke(Expression.MethodInvoke expr) {
    List<ExecutableExpression> args = expr.getArgs().stream().map(this::apply).collect(Collectors.toList());
    Supplier<ExecutableMethod> body = () -> expr.isBuiltIn()
      ? builtInMethods.apply(expr.getMethodId())
      : methods.apply(expr.getMethodId());
    return new ExecutableExpression.MethodInvokeExpression(expr, args, body);
  }

  private ExecutableExpression unrecognizedExpr(Expression.UnrecognizedExpression expr) {
    throw new IllegalArgumentException(expr.toString());
  }

  private ExecutableExpression varExpr(Expression.VarExpression var) {
    return new ExecutableExpression.VarReadExpression(var);
  }
}

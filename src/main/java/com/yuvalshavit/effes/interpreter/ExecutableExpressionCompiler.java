package com.yuvalshavit.effes.interpreter;

import com.yuvalshavit.effes.compile.CaseMatcher;
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
      .put(Expression.ArgExpression.class, ExecutableExpressionCompiler::argExpr)
      .put(Expression.CaseExpression.class, ExecutableExpressionCompiler::caseExpr)
      .put(Expression.MethodInvoke.class, ExecutableExpressionCompiler::methodInvoke)
      .put(Expression.CtorInvoke.class, ExecutableExpressionCompiler::ctorInvoke)
      .put(Expression.VarExpression.class, ExecutableExpressionCompiler::varExpr)
      .put(Expression.UnrecognizedExpression.class, ExecutableExpressionCompiler::unrecognizedExpr)
      .build();

  private ExecutableExpression caseExpr(Expression.CaseExpression expr) {
    ExecutableExpression matchAgainst = apply(expr.getMatchAgainst());
    List<ExecutableExpression.CaseExpression.CaseMatcher> matchers = expr.getPatterns().stream().map(p -> {
      ExecutableExpression.PatternMatch match = caseMatcherDispatch.apply(this, p.getMatcher());
      ExecutableExpression ifMatch = apply(p.getIfMatchedExpression());
      return new ExecutableExpression.CaseExpression.CaseMatcher(match, ifMatch);
    }).collect(Collectors.toList());
    return new ExecutableExpression.CaseExpression(expr, matchAgainst, matchers);
  }

  private ExecutableExpression ctorInvoke(Expression.CtorInvoke expr) {
    return new ExecutableExpression.CtorExpression(expr);
  }

  private ExecutableExpression methodInvoke(Expression.MethodInvoke expr) {
    List<ExecutableExpression> args = expr.getArgs().stream().map(this::apply).collect(Collectors.toList());
    EfMethod<? extends ExecutableElement> method = methods.getMethod(expr.getMethodName());
    assert method != null;
    return new ExecutableExpression.MethodInvokeExpression(expr, args, method.getBody());
  }

  private ExecutableExpression unrecognizedExpr(Expression.UnrecognizedExpression expr) {
    throw new IllegalArgumentException(expr.toString());
  }

  private ExecutableExpression varExpr(Expression.VarExpression var) {
    return new ExecutableExpression.VarReadExpression(var);
  }

  private ExecutableExpression argExpr(Expression.ArgExpression arg) {
    return new ExecutableExpression.ArgReadExpression(arg);
  }

  private static final Dispatcher<ExecutableExpressionCompiler, CaseMatcher, ExecutableExpression.PatternMatch>
    caseMatcherDispatch =
    Dispatcher.builder(ExecutableExpressionCompiler.class, CaseMatcher.class, ExecutableExpression.PatternMatch.class)
      .put(CaseMatcher.SimpleCtorMatch.class, ExecutableExpressionCompiler::simpleCtorMatch)
    .build();

  private ExecutableExpression.PatternMatch simpleCtorMatch(CaseMatcher.SimpleCtorMatch matcher) {
    return s -> matcher.getType().equals(s.peek());
  }
}

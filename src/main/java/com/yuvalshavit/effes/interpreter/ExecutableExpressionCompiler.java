package com.yuvalshavit.effes.interpreter;

import com.yuvalshavit.effes.compile.CaseMatcher;
import com.yuvalshavit.effes.compile.Expression;
import com.yuvalshavit.util.Dispatcher;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class ExecutableExpressionCompiler implements Function<Expression, ExecutableExpression> {

  private final Function<String, ExecutableMethod> methods;
  private final Function<String, ExecutableMethod> builtInMethods;

  public ExecutableExpressionCompiler(Function<String, ExecutableMethod> methods,
                                      Function<String, ExecutableMethod> builtInMethods) {
    this.methods = methods;
    this.builtInMethods = builtInMethods;
  }

  @Override
  public ExecutableExpression apply(Expression expression) {
    return dispatcher.apply(this, expression);
  }

  private static final Dispatcher<ExecutableExpressionCompiler, Expression, ExecutableExpression> dispatcher =
    Dispatcher.builder(ExecutableExpressionCompiler.class, Expression.class, ExecutableExpression.class)
      .put(Expression.CaseExpression.class, ExecutableExpressionCompiler::caseExpr)
      .put(Expression.MethodInvoke.class, ExecutableExpressionCompiler::methodInvoke)
      .put(Expression.CtorInvoke.class, ExecutableExpressionCompiler::ctorInvoke)
      .put(Expression.VarExpression.class, ExecutableExpressionCompiler::varExpr)
      .put(Expression.UnrecognizedExpression.class, ExecutableExpressionCompiler::unrecognizedExpr)
      .build((me, e) -> {throw new AssertionError(e); });

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

  public ExecutableExpression methodInvoke(Expression.MethodInvoke expr) {
    List<ExecutableExpression> args = expr.getArgs().stream().map(this::apply).collect(Collectors.toList());
    Supplier<ExecutableMethod> body = () -> expr.isBuiltIn()
      ? builtInMethods.apply(expr.getMethodName())
      : methods.apply(expr.getMethodName());
    return new ExecutableExpression.MethodInvokeExpression(expr, args, body);
  }

  private ExecutableExpression unrecognizedExpr(Expression.UnrecognizedExpression expr) {
    throw new IllegalArgumentException(expr.toString());
  }

  private ExecutableExpression varExpr(Expression.VarExpression var) {
    return var.isArg()
      ? new ExecutableExpression.ArgReadExpression(var)
      : new ExecutableExpression.VarReadExpression(var);
  }

  private static final Dispatcher<ExecutableExpressionCompiler, CaseMatcher, ExecutableExpression.PatternMatch>
    caseMatcherDispatch =
    Dispatcher.builder(ExecutableExpressionCompiler.class, CaseMatcher.class, ExecutableExpression.PatternMatch.class)
      .put(CaseMatcher.SimpleCtorMatch.class, ExecutableExpressionCompiler::simpleCtorMatch)
      .put(CaseMatcher.ErrorMatch.class, (me, c) -> { throw new AssertionError(c); })
      .build((me, c) -> { throw new AssertionError(c); });

  private ExecutableExpression.PatternMatch simpleCtorMatch(CaseMatcher.SimpleCtorMatch matcher) {
    return s -> matcher.getType().equals(s.peek());
  }
}

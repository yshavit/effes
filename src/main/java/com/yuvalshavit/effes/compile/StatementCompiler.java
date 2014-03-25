package com.yuvalshavit.effes.compile;

import com.google.common.collect.Lists;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.util.Dispatcher;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class StatementCompiler implements Function<EffesParser.StatContext, Statement> {
  private final StatementDispatcher dispatcher;

  public StatementCompiler(ExpressionCompiler expressionCompiler, MethodsRegistry<?> methodsRegistry) {
    this.dispatcher = new StatementDispatcher(expressionCompiler, methodsRegistry);
  }

  @Override
  public Statement apply(EffesParser.StatContext statContext) {
    return StatementDispatcher.dispatcher.apply(dispatcher, statContext);
  }

  static class StatementDispatcher {
    static final Dispatcher<StatementDispatcher, EffesParser.StatContext, Statement> dispatcher =
      Dispatcher.builder(StatementDispatcher.class, EffesParser.StatContext.class, Statement.class)
        .put(EffesParser.MethodInvokeContext.class, StatementDispatcher::methodInvoke)
        .put(EffesParser.ReturnStatContext.class, StatementDispatcher::returnStat)
        .build();

    private final ExpressionCompiler expressionCompiler;
    private final MethodsRegistry<?> methodsRegistry;

    StatementDispatcher(ExpressionCompiler expressionCompiler, MethodsRegistry<?> methodsRegistry) {
      this.expressionCompiler = expressionCompiler;
      this.methodsRegistry = methodsRegistry;
    }

    public Statement methodInvoke(EffesParser.MethodInvokeContext ctx) {
      String methodName = ctx.methodName().getText();
      EfMethod<?> method = methodsRegistry.getMethod(methodName);
      if (method == null) {
        throw new StatementCompilationException("no such method: " + methodName);
      }
      List<Expression> invokeArgs = ctx.methodInvokeArgs().expr().stream()
        .map(expressionCompiler::apply).collect(Collectors.toList());
      List<SimpleType> invokeArgTypes = Lists.transform(invokeArgs, Expression::resultType);
      List<SimpleType> expectedArgs = method.getArgTypes();
      if (!invokeArgTypes.equals(expectedArgs)) {
        throw new StatementCompilationException(
          String.format("mismatched types: expected %s but found %s", expectedArgs, invokeArgTypes));
      }
      return new Statement.MethodInvoke(methodName, invokeArgs);
    }

    public Statement returnStat(EffesParser.ReturnStatContext ctx) {
      Expression expr = expressionCompiler.apply(ctx.exprLine().expr());
      return new Statement.ReturnStatement(expr);
    }
  }
}

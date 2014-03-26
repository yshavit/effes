package com.yuvalshavit.effes.compile;

import com.google.common.collect.Lists;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.util.Dispatcher;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class StatementCompiler implements Function<EffesParser.StatContext, Statement> {

  private final ExpressionCompiler expressionCompiler;
  private final MethodsRegistry<?> methodsRegistry;
  private final MethodsRegistry<?> builtInMethods;

  public StatementCompiler(ExpressionCompiler expressionCompiler,
                           MethodsRegistry<?> methodsRegistry,
                           MethodsRegistry<?> builtInMethods) {
    this.expressionCompiler = expressionCompiler;
    this.methodsRegistry = methodsRegistry;
    this.builtInMethods = builtInMethods;
  }

  @Override
  public Statement apply(EffesParser.StatContext statContext) {
    return dispatcher.apply(this, statContext);
  }

  static final Dispatcher<StatementCompiler, EffesParser.StatContext, Statement> dispatcher =
    Dispatcher.builder(StatementCompiler.class, EffesParser.StatContext.class, Statement.class)
      .put(EffesParser.MethodInvokeContext.class, StatementCompiler::methodInvoke)
      .put(EffesParser.ReturnStatContext.class, StatementCompiler::returnStat)
      .build();

  private Statement methodInvoke(EffesParser.MethodInvokeContext ctx) {
    String methodName = ctx.methodName().getText();
    EfMethod<?> method = methodsRegistry.getMethod(methodName);
    boolean isBuiltIn;
    if (method != null) {
      isBuiltIn = false;
    } else {
      method = builtInMethods.getMethod(methodName);
      if (method == null) {
        throw new StatementCompilationException(ctx.methodName().getStart(), "no such method: " + methodName);
      } isBuiltIn = true;
    }

    List<Expression> invokeArgs = ctx.methodInvokeArgs().expr().stream()
      .map(expressionCompiler::apply).collect(Collectors.toList());
    List<SimpleType> invokeArgTypes = Lists.transform(invokeArgs, Expression::resultType);
    List<SimpleType> expectedArgs = method.getArgTypes();
    if (!invokeArgTypes.equals(expectedArgs)) {
      throw new StatementCompilationException(
        ctx.methodInvokeArgs().getStart(),
        String.format("mismatched types: expected %s but found %s", expectedArgs, invokeArgTypes));
    }
    return new Statement.MethodInvoke(ctx.getStart(), methodName, invokeArgs, method.getResultType(), isBuiltIn);
  }

  private Statement returnStat(EffesParser.ReturnStatContext ctx) {
    Expression expr = expressionCompiler.apply(ctx.exprLine().expr());
    return new Statement.ReturnStatement(ctx.getStart(), expr);
  }
}

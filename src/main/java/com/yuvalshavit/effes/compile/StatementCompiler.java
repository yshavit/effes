package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.util.Dispatcher;

import java.util.function.Function;

public final class StatementCompiler implements Function<EffesParser.StatContext, Statement> {
  private final StatementDispatcher dispatcher;

  public StatementCompiler(ExpressionCompiler expressionCompiler) {
    this.dispatcher = new StatementDispatcher(expressionCompiler);
  }

  @Override
  public Statement apply(EffesParser.StatContext statContext) {
    return StatementDispatcher.dispatcher.apply(dispatcher, statContext);
  }

  static class StatementDispatcher {
    static final Dispatcher<StatementDispatcher, EffesParser.StatContext, Statement> dispatcher =
      Dispatcher.<StatementDispatcher, EffesParser.StatContext, Statement>builder(EffesParser.StatContext.class)
        .put(EffesParser.ReturnStatContext.class, StatementDispatcher::returnStat)
        .build();

    private final ExpressionCompiler expressionCompiler;

    StatementDispatcher(ExpressionCompiler expressionCompiler) {
      this.expressionCompiler = expressionCompiler;
    }

    public Statement returnStat(EffesParser.ReturnStatContext ctx) {
      Expression expr = expressionCompiler.apply(ctx.exprLine().expr());
      return new Statement.ReturnStatement(expr);
    }
  }
}

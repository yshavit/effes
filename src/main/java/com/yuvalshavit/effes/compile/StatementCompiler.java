package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.util.Dispatcher;
import org.antlr.v4.runtime.Token;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public final class StatementCompiler implements Function<EffesParser.StatContext, Statement> {

  private final ExpressionCompiler expressionCompiler;
  private final Scopes<EfVar, Token> vars;

  public StatementCompiler(ExpressionCompiler expressionCompiler, Scopes<EfVar, Token> vars) {
    this.expressionCompiler = expressionCompiler;
    this.vars = vars;
  }

  @Override
  public Statement apply(EffesParser.StatContext statContext) {
    return dispatcher.apply(this, statContext);
  }

  static final Dispatcher<StatementCompiler, EffesParser.StatContext, Statement> dispatcher =
    Dispatcher.builder(StatementCompiler.class, EffesParser.StatContext.class, Statement.class)
      .put(EffesParser.AssignStatContext.class, StatementCompiler::assignStatement)
      .put(EffesParser.MethodInvokeStatContext.class, StatementCompiler::methodInvoke)
      .put(EffesParser.ReturnStatContext.class, StatementCompiler::returnStat)
      .build();

  public static final java.util.concurrent.atomic.AtomicBoolean sawOneAssignment = new AtomicBoolean(false);
  private Statement assignStatement(EffesParser.AssignStatContext ctx) {
    if (!sawOneAssignment.compareAndSet(false, true))
      throw new IllegalStateException("Effes only supports one variable for now! Globally!");
    Expression value = expressionCompiler.apply(ctx.exprLine());
    int pos = 0; // TODO
    EfVar var = EfVar.arg(ctx.VAR_NAME().getText(), pos, value.resultType());
    vars.add(var, ctx.VAR_NAME().getSymbol());
    return new Statement.AssignStatement(ctx.getStart(), var, value);
  }

  private Statement methodInvoke(EffesParser.MethodInvokeStatContext ctx) {
    return new Statement.MethodInvoke(expressionCompiler.methodInvoke(ctx.methodInvoke()));
  }

  private Statement returnStat(EffesParser.ReturnStatContext ctx) {
    Expression expr = expressionCompiler.apply(ctx.exprLine());
    return new Statement.ReturnStatement(ctx.exprLine().getStart(), expr);
  }
}

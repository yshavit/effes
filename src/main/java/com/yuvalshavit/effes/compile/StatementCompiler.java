package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.util.Dispatcher;
import org.antlr.v4.runtime.Token;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

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
      .put(EffesParser.CaseStatContext.class, StatementCompiler::caseStatement)
      .build(StatementCompiler::error);

  private Statement assignStatement(EffesParser.AssignStatContext ctx) {
    Expression value = expressionCompiler.apply(ctx.exprLine());
    int pos = vars.countElems();
    EfVar var = EfVar.var(ctx.VAR_NAME().getText(), pos, value.resultType());
    vars.add(var, ctx.VAR_NAME().getSymbol());
    return new Statement.AssignStatement(ctx.getStart(), var, value);
  }

  private Statement caseStatement(EffesParser.CaseStatContext ctx) {
    Expression matchAgainst = expressionCompiler.apply(ctx.expr());
    List<CaseConstruct.Alternative<Block>> alternatives =
      ctx.caseStatAlternative().stream().map(this::caseAlternative).collect(Collectors.toList());
    throw new UnsupportedOperationException(); // TODO
  }

  private Statement methodInvoke(EffesParser.MethodInvokeStatContext ctx) {
    return new Statement.MethodInvoke(expressionCompiler.methodInvoke(ctx.methodInvoke(), false));
  }

  private Statement returnStat(EffesParser.ReturnStatContext ctx) {
    Expression expr = expressionCompiler.apply(ctx.exprLine());
    return new Statement.ReturnStatement(ctx.exprLine().getStart(), expr);
  }

  private Statement error(EffesParser.StatContext ctx) {
    return new Statement.UnrecognizedStatement(ctx.getStart());
  }

  private CaseConstruct.Alternative<Block> caseAlternative(EffesParser.CaseStatAlternativeContext ctx) {
    throw new UnsupportedOperationException(); // TODO
  }
}

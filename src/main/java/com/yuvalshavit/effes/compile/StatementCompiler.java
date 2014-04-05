package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.util.Dispatcher;
import org.antlr.v4.runtime.Token;

import java.util.List;
import java.util.Objects;
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
    EfType varType = value.resultType();
    if (EfType.VOID.equals(varType)) {
      // We have something like "foo = bar" where bar is a method with no return value. This doesn't make sense!
      // But rather than saying that foo has a void type, we'll just say we don't know what its type is.
      varType = EfType.UNKNOWN;
    }
    EfVar var = EfVar.var(ctx.VAR_NAME().getText(), pos, varType);
    vars.add(var, ctx.VAR_NAME().getSymbol());
    return new Statement.AssignStatement(ctx.getStart(), var, value);
  }

  private Statement caseStatement(EffesParser.CaseStatContext ctx) {
    Expression matchAgainst = expressionCompiler.apply(ctx.expr());
    List<CaseConstruct.Alternative<Block>> alternatives =
      ctx.caseStatAlternative().stream()
        .map(this::caseAlternative).filter(Objects::nonNull).collect(Collectors.toList());
    CaseConstruct<Block> construct = new CaseConstruct<>(matchAgainst, alternatives);
    return new Statement.CaseStatement(ctx.getStart(), construct);
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
    return expressionCompiler.caseAlternative(
      ctx,
      EffesParser.CaseStatAlternativeContext::casePattern,
      c -> c.inlinableBlock() != null
        ? new Block(c.getStart(), c.inlinableBlock().stat().stream().map(this::apply).collect(Collectors.toList()))
        : null
    );
  }

}

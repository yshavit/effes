package com.yuvalshavit.effes.compile;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Pair;

import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.yuvalshavit.effes.compile.node.Block;
import com.yuvalshavit.effes.compile.node.CaseConstruct;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.effes.compile.node.EfVar;
import com.yuvalshavit.effes.compile.node.Expression;
import com.yuvalshavit.effes.compile.node.Node;
import com.yuvalshavit.effes.compile.node.Statement;
import com.yuvalshavit.effes.compile.pmatch.PAlternativeSubtractionResult;
import com.yuvalshavit.effes.compile.pmatch.PAlternative;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.util.Dispatcher;

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
      .put(EffesParser.InstanceMethodInvokeStatContext.class, StatementCompiler::instanceMethodInvoke)
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
    EfVar matchAgainstVar = expressionCompiler.tryGetEfVar(matchAgainst);
    PAlternativeSubtractionResult matchAgainstPossibility = new PAlternativeSubtractionResult(matchAgainst.resultType());
    List<CaseConstruct.Alternative<Block>> alternatives = new ArrayList<>(ctx.caseStatAlternative().size());
    for (EffesParser.CaseStatAlternativeContext caseStatAltCtx : ctx.caseStatAlternative()) {
      CaseConstruct.Alternative<Block> step = caseAlternative(
        caseStatAltCtx.casePattern(),
        matchAgainstPossibility.efType(),
        matchAgainstVar,
        () -> new Block(caseStatAltCtx.inlinableBlock().getStart(), Lists.transform(caseStatAltCtx.inlinableBlock().stat(), this::apply)));
      alternatives.add(step);
      PAlternativeSubtractionResult nextPossibility = step.getPAlternative().subtractFrom(matchAgainstPossibility);
      if (nextPossibility == null) {
        throw new UnsupportedOperationException("register the error, substitute UNKNOWN"); // TODO
      }
      matchAgainstPossibility = nextPossibility;
    }
    CaseConstruct<Block> construct = new CaseConstruct<>(matchAgainst, alternatives);
    return new Statement.CaseStatement(ctx.getStart(), construct);
  }

  public <N extends Node> CaseConstruct.Alternative<N> caseAlternative(
    EffesParser.CasePatternContext ctx,
    EfType matchAgainstType,
    EfVar matchAgainstVar,
    Supplier<N> matchAgainstNode)
  {
    Pair<PAlternative,EfType> pattern = expressionCompiler.compilePattern(ctx, matchAgainstType);
    if (pattern == null) {
      throw new UnsupportedOperationException(); // TODO
    }
    if (matchAgainstVar != null) {
      vars.pushScope();
      vars.replace(matchAgainstVar.cast(pattern.b));
    }
    N compiledNode = matchAgainstNode.get();
    if (matchAgainstVar != null) {
      vars.popScope();
    }
    return new CaseConstruct.Alternative<>(pattern.a, compiledNode);
  }

  private Statement instanceMethodInvoke(EffesParser.InstanceMethodInvokeStatContext ctx) {
    if (ctx.expr() == null || ctx.methodInvoke() == null) {
      return new Statement.UnrecognizedStatement(ctx.getStart());
    }
    Expression target = expressionCompiler.apply(ctx.expr());
    return new Statement.MethodInvoke(expressionCompiler.methodInvoke(ctx.methodInvoke(), false, target));
  }

  private Statement methodInvoke(EffesParser.MethodInvokeStatContext ctx) {
    if (ctx.methodInvoke() == null) {
      return new Statement.UnrecognizedStatement(ctx.getStart());
    }
    return new Statement.MethodInvoke(expressionCompiler.methodInvoke(ctx.methodInvoke(), false, null));
  }

  private Statement returnStat(EffesParser.ReturnStatContext ctx) {
    Expression expr = expressionCompiler.apply(ctx.exprLine());
    return new Statement.ReturnStatement(ctx.exprLine().getStart(), expr);
  }

  private Statement error(EffesParser.StatContext ctx) {
    return new Statement.UnrecognizedStatement(ctx.getStart());
  }

}

package com.yuvalshavit.effes.compile.expr;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.yuvalshavit.effes.base.Type;
import com.yuvalshavit.effes.ir.Block;
import com.yuvalshavit.effes.ir.Expression;
import com.yuvalshavit.effes.ir.Statement;
import com.yuvalshavit.effes.parser.EffesParser;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public final class StatementCompiler {

  private final ExpressionCompiler expressionCompiler;
  private final Type declaringContext;

  public StatementCompiler(ExpressionCompiler expressionCompiler, Type declaringContext) {
    this.expressionCompiler = expressionCompiler;
    this.declaringContext = declaringContext;
  }

  public Statement run(EffesParser.StatContext ctx) {
    return new DispatchImpl().createStat(ctx);
  }

  private static Map<Class<?>, BiFunction<Dispatch, Object, Statement>> createDispatchMap() {
    ImmutableMap.Builder<Class<?>, BiFunction<Dispatch, Object, Statement>> builder = ImmutableMap.builder();
    builder.put(EffesParser.IfElseStatContext.class, (d, o) -> d.ifElseStat((EffesParser.IfElseStatContext) o));
    builder.put(EffesParser.CaseStatContext.class, (d, o) -> d.caseStat((EffesParser.CaseStatContext) o));
    builder.put(EffesParser.AssignStatContext.class, (d, o) -> d.assignStat((EffesParser.AssignStatContext) o));
    builder.put(EffesParser.ReturnStatContext.class, (d, o) -> d.returnStat((EffesParser.ReturnStatContext) o));
    return builder.build();
  }

  private class DispatchImpl implements Dispatch {
    @Override
    public Statement ifElseStat(EffesParser.IfElseStatContext ctx) {
      // Effes defines if-elses as IF, ELSEIF*, ELSE?
      // We want to instead create nested if-[else] statements. We'll start with the ELSE, and work
      // *backwards* adding each else-if.
      Block elseBlock = ctx.elseStatFragment() != null
        ? block(ctx.elseStatFragment().block())
        : null;

      for (EffesParser.ElseIfStatFragmentContext elseIf : Lists.reverse(ctx.elseIfStatFragment())) {
        Expression cond = expressionCompiler.run(declaringContext, elseIf.expr());
        Block block = block(elseIf.block());
        Statement elseIfStat = new Statement.IfElseStatement(cond, block, elseBlock);
        elseBlock = new Block(ImmutableList.of(elseIfStat));
      }

      Expression cond = expressionCompiler.run(declaringContext, ctx.ifStatFragment().expr());
      Block block = block(ctx.ifStatFragment().block());

      return new Statement.IfElseStatement(cond, block, elseBlock);
    }

    @Override
    public Statement assignStat(EffesParser.AssignStatContext ctx) {
      String varName = ctx.VAR_NAME().getText();
      Expression value = expressionCompiler.run(declaringContext, ctx.exprLine());
      return new Statement.AssignStat(varName, value);
    }

    @Override
    public Statement returnStat(EffesParser.ReturnStatContext ctx) {
      return new Statement.ReturnStat(expressionCompiler.run(declaringContext, ctx.exprLine()));
    }

    @Override
    public Statement caseStat(EffesParser.CaseStatContext ctx) {
      throw new UnsupportedOperationException(); // TODO
    }

    private Block block(EffesParser.BlockContext b) {
      return new Block(createStats(b.stat()));
    }
  }

  interface Dispatch {

    static Map<Class<?>, BiFunction<Dispatch, Object, Statement>> dispatchMap = createDispatchMap();

    default Statement createStat(EffesParser.StatContext ctx) {
      BiFunction<Dispatch, Object, Statement> dispatch = dispatchMap.get(ctx.getClass());
      assert dispatch != null: "no dispatch for " + ctx.getClass();
      return dispatch.apply(this, ctx);
    }

    default List<Statement> createStats(List<EffesParser.StatContext> ctxs) {
      return ctxs.stream().map(this::createStat).collect(Collectors.toList());
    }

    Statement ifElseStat(EffesParser.IfElseStatContext ctx);
    Statement caseStat(EffesParser.CaseStatContext ctx);
    Statement assignStat(EffesParser.AssignStatContext ctx);
    Statement returnStat(EffesParser.ReturnStatContext ctx);
  }
}

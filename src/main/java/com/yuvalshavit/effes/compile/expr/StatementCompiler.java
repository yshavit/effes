package com.yuvalshavit.effes.compile.expr;

import com.google.common.collect.ImmutableMap;
import com.yuvalshavit.effes.ir.Statement;
import com.yuvalshavit.effes.parser.EffesParser;

import java.util.Map;
import java.util.function.BiFunction;

public final class StatementCompiler {

  private static Map<Class<?>, BiFunction<Dispatch, Object, Statement>> createDispatchMap() {
    ImmutableMap.Builder<Class<?>, BiFunction<Dispatch, Object, Statement>> builder = ImmutableMap.builder();
    builder.put(EffesParser.IfElseStatContext.class, (d, o) -> d.ifElseStat((EffesParser.IfElseStatContext) o));
    builder.put(EffesParser.CaseStatContext.class, (d, o) -> d.caseStat((EffesParser.CaseStatContext) o));
    builder.put(EffesParser.AssignStatContext.class, (d, o) -> d.assignStat((EffesParser.AssignStatContext) o));
    builder.put(EffesParser.ReturnStatContext.class, (d, o) -> d.returnStat((EffesParser.ReturnStatContext) o));
    return builder.build();
  }

  interface Dispatch {

    static Map<Class<?>, BiFunction<Dispatch, Object, Statement>> dispatchMap = createDispatchMap();

    default Statement createStat(EffesParser.StatContext ctx) {
      BiFunction<Dispatch, Object, Statement> dispatch = dispatchMap.get(ctx.getClass());
      assert dispatch != null: "no dispatch for " + ctx.getClass();
      return dispatch.apply(this, ctx);
    }

    Statement ifElseStat(EffesParser.IfElseStatContext ctx);
    Statement caseStat(EffesParser.CaseStatContext ctx);
    Statement assignStat(EffesParser.AssignStatContext ctx);
    Statement returnStat(EffesParser.ReturnStatContext ctx);
  }
}

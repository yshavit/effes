package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.util.Dispatcher;

import java.util.function.Function;

public final class ExpressionCompiler implements Function<EffesParser.ExprContext, Expression> {
  private final TypeRegistry typeRegistry;

  public ExpressionCompiler(TypeRegistry typeRegistry) {
    this.typeRegistry = typeRegistry;
  }

  @Override
  public Expression apply(EffesParser.ExprContext exprContext) {
    return dispatcher.apply(this, exprContext);
  }

  private static final Dispatcher<ExpressionCompiler, EffesParser.ExprContext, Expression> dispatcher =
    Dispatcher.builder(ExpressionCompiler.class, EffesParser.ExprContext.class, Expression.class)
      .put(EffesParser.ParenExprContext.class, ExpressionCompiler::paren)
      .put(EffesParser.CtorInvokeContext.class, ExpressionCompiler::ctorInvoke)
      .build();

  private Expression paren(EffesParser.ParenExprContext ctx) {
    return dispatcher.apply(this, ctx.expr());
  }

  private Expression ctorInvoke(EffesParser.CtorInvokeContext ctx) {
    String typeName = ctx.TYPE_NAME().getText();
    SimpleType type = typeRegistry.getSimpleType(typeName);
    if (type == null) {
      throw new ExpressionCompilationException("unknown type: " + typeName);
    }
    return new Expression.CtorInvoke(type);
  }
}

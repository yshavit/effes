package com.yuvalshavit.effes.compile;

import com.google.common.annotations.VisibleForTesting;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.util.Dispatcher;

import java.util.function.Function;

public final class ExpressionCompiler implements Function<EffesParser.ExprContext, Expression> {
  private final ExpressionDispatcher dispatcher;

  public ExpressionCompiler(TypeRegistry typeRegistry) {
    this.dispatcher = new ExpressionDispatcher(typeRegistry);
  }

  @Override
  public Expression apply(EffesParser.ExprContext exprContext) {
    return ExpressionDispatcher.dispatcher.apply(dispatcher, exprContext);
  }

  @VisibleForTesting
  static class ExpressionDispatcher {
    static final Dispatcher<ExpressionDispatcher, EffesParser.ExprContext, Expression> dispatcher =
      Dispatcher.builder(ExpressionDispatcher.class, EffesParser.ExprContext.class, Expression.class)
        .put(EffesParser.ParenExprContext.class, ExpressionDispatcher::paren)
        .put(EffesParser.CtorInvokeContext.class, ExpressionDispatcher::ctorInvoke)
        .build();

    private final TypeRegistry typeRegistry;

    ExpressionDispatcher(TypeRegistry typeRegistry) {
      this.typeRegistry = typeRegistry;
    }

    public Expression paren(EffesParser.ParenExprContext ctx) {
      return dispatcher.apply(this, ctx.expr());
    }

    public Expression ctorInvoke(EffesParser.CtorInvokeContext ctx) {
      String typeName = ctx.TYPE_NAME().getText();
      SimpleType type = typeRegistry.getSimpleType(typeName);
      if (type == null) {
        throw new ExpressionCompilationException("unknown type: " + typeName);
      }
      return new Expression.CtorInvoke(type);
    }
  }
}

package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.util.Dispatcher;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ExpressionCompiler implements Function<EffesParser.ExprContext, Expression> {
  private final MethodsRegistry<?> methodsRegistry;
  private final TypeRegistry typeRegistry;
  private final CompileErrors errs;

  public ExpressionCompiler(MethodsRegistry<?> methodsRegistry, TypeRegistry typeRegistry, CompileErrors errs) {
    this.methodsRegistry = methodsRegistry;
    this.typeRegistry = typeRegistry;
    this.errs = errs;
  }

  @Override
  public Expression apply(EffesParser.ExprContext exprContext) {
    return dispatcher.apply(this, exprContext);
  }

  private static final Dispatcher<ExpressionCompiler, EffesParser.ExprContext, Expression> dispatcher =
    Dispatcher.builder(ExpressionCompiler.class, EffesParser.ExprContext.class, Expression.class)
      .put(EffesParser.MethodInvokeExprContext.class, ExpressionCompiler::methodInvoke)
      .put(EffesParser.ParenExprContext.class, ExpressionCompiler::paren)
      .put(EffesParser.CtorInvokeContext.class, ExpressionCompiler::ctorInvoke)
      .build();

  private Expression methodInvoke(EffesParser.MethodInvokeExprContext ctx) {
    String methodName = ctx.methodName().getText();
    EfMethod<?> method = methodsRegistry.getMethod(methodName);
    EfType resultType;
    if (method == null) {
      errs.add(ctx.methodName().getStart(), "no such method: " + methodName);
      resultType = EfType.UNKNOWN;
    } else {
      resultType = method.getResultType();
    }
    List<Expression> args = ctx
      .methodInvokeArgs()
      .expr()
      .stream()
      .map(this::apply)
      .collect(Collectors.toList());
    return new Expression.MethodInvoke(ctx.getStart(), methodName, method, args, resultType);
  }

  private Expression paren(EffesParser.ParenExprContext ctx) {
    return dispatcher.apply(this, ctx.expr());
  }

  private Expression ctorInvoke(EffesParser.CtorInvokeContext ctx) {
    String typeName = ctx.TYPE_NAME().getText();
    EfType.SimpleType type = typeRegistry.getSimpleType(typeName);
    if (type == null) {
      errs.add(ctx.getStart(), "unknown type: " + typeName);
    }
    return new Expression.CtorInvoke(ctx.getStart(), type);
  }
}

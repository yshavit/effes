package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.util.Dispatcher;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ExpressionCompiler {
  private final MethodsRegistry<?> methodsRegistry;
  private final TypeRegistry typeRegistry;
  private final CompileErrors errs;
  private final Scopes<EfVar, Token> vars;

  public ExpressionCompiler(MethodsRegistry<?> methodsRegistry,
                            TypeRegistry typeRegistry,
                            CompileErrors errs,
                            Scopes<EfVar, Token> vars)
  {
    this.methodsRegistry = methodsRegistry;
    this.typeRegistry = typeRegistry;
    this.errs = errs;
    this.vars = vars;
  }

  public Expression apply(EffesParser.ExprContext exprContext) {
    return dispatcher.apply(this, exprContext);
  }

  public Expression apply(EffesParser.ExprLineContext exprLineContext) {
    return exprLineDispatcher.apply(this, exprLineContext);
  }

  private static final Dispatcher<ExpressionCompiler, EffesParser.ExprContext, Expression> dispatcher =
    Dispatcher.builder(ExpressionCompiler.class, EffesParser.ExprContext.class, Expression.class)
      .put(EffesParser.MethodInvokeExprContext.class, ExpressionCompiler::methodInvoke)
      .put(EffesParser.ParenExprContext.class, ExpressionCompiler::paren)
      .put(EffesParser.CtorInvokeContext.class, ExpressionCompiler::ctorInvoke)
      .put(EffesParser.VarExprContext.class, ExpressionCompiler::varExpr)
      .setErrHandler(ExpressionCompiler::error)
      .build();

  private Expression error(EffesParser.ExprContext ctx) {
    errs.add(ctx.getStart(), "unrecognized expression");
    return new Expression.UnrecognizedExpression(ctx.getStart());
  }

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

  private Expression varExpr(EffesParser.VarExprContext ctx) {
    TerminalNode name = ctx.VAR_NAME();
    EfVar var = vars.get(name.getText());
    if (var == null) {
      errs.add(name.getSymbol(), "unrecognized variable '" + name.getText() + '\'');
      var = EfVar.unknown(name.getText());
      vars.add(var, name.getSymbol());
      return new Expression.VarExpression(name.getSymbol(), var);
    } else {
      return new Expression.ArgExpression(name.getSymbol(), var);
    }
  }

  private static final Dispatcher<ExpressionCompiler, EffesParser.ExprLineContext, Expression> exprLineDispatcher
    = Dispatcher.builder(ExpressionCompiler.class, EffesParser.ExprLineContext.class, Expression.class)
    .put(EffesParser.SingleLineExpressionContext.class, ExpressionCompiler::singleLine)
    .build();

  private Expression singleLine(EffesParser.SingleLineExpressionContext ctx) {
    return apply(ctx.expr());
  }

  private Expression multiLine(EffesParser.MultilineExprContext ctx) {
    throw new UnsupportedOperationException(); // TODO
  }
}

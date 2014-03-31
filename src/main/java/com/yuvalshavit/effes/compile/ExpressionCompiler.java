package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.util.Dispatcher;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;
import java.util.stream.Collectors;

public final class ExpressionCompiler {
  private final MethodsRegistry<?> methodsRegistry;
  private final MethodsRegistry<?> builtInMethods;
  private final TypeRegistry typeRegistry;
  private final CompileErrors errs;
  private final Scopes<EfVar, Token> vars;

  public ExpressionCompiler(MethodsRegistry<?> methodsRegistry,
                            MethodsRegistry<?> builtInMethods,
                            TypeRegistry typeRegistry,
                            CompileErrors errs,
                            Scopes<EfVar, Token> vars)
  {
    this.methodsRegistry = methodsRegistry;
    this.builtInMethods = builtInMethods;
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
      .build(ExpressionCompiler::error);

  private Expression error(EffesParser.ExprContext ctx) {
    errs.add(ctx.getStart(), "unrecognized expression");
    return new Expression.UnrecognizedExpression(ctx.getStart());
  }

  private Expression methodInvoke(EffesParser.MethodInvokeExprContext ctx) {
    return methodInvoke(ctx.methodInvoke());
  }

  public Expression.MethodInvoke methodInvoke(EffesParser.MethodInvokeContext ctx) {
    String methodName = ctx.methodName().getText();
    EfMethod<?> method = methodsRegistry.getMethod(methodName);
    boolean isBuiltIn;
    if (method != null) {
      isBuiltIn = false;
    } else {
      method = builtInMethods.getMethod(methodName);
      if (method == null) {
        errs.add(ctx.methodName().getStart(), "no such method: " + methodName);
      }
      isBuiltIn = true; // if method is null, this won't matter
    }

    List<Expression> invokeArgs = ctx.methodInvokeArgs().expr().stream()
      .map(this::apply).collect(Collectors.toList());
    EfType resultType;
    if (method != null) {
      resultType = method.getResultType();
      List<EfType> expectedArgs = method.getArgs().viewTypes();
      for (int i = 0, len = Math.min(invokeArgs.size(), expectedArgs.size()); i < len; ++i) {
        EfType invokeArg = invokeArgs.get(i).resultType();
        EfType expectedArg = expectedArgs.get(i);
        // e.g. method (True | False), arg is True
        if (!expectedArg.contains(invokeArg)) {
          errs.add(
            invokeArgs.get(i).token(),
            String.format("mismatched types for '%s': expected %s but found %s", methodName, expectedArg, invokeArg));
        }
      }
    } else {
      resultType = EfType.UNKNOWN;
    }
    return new Expression.MethodInvoke(ctx.getStart(), methodName, invokeArgs, resultType, isBuiltIn);
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
      return var.isArg()
        ? new Expression.ArgExpression(name.getSymbol(), var)
        : new Expression.VarExpression(name.getSymbol(), var);
    }
  }

  private static final Dispatcher<ExpressionCompiler, EffesParser.ExprLineContext, Expression> exprLineDispatcher
    = Dispatcher.builder(ExpressionCompiler.class, EffesParser.ExprLineContext.class, Expression.class)
    .put(EffesParser.SingleLineExpressionContext.class, ExpressionCompiler::singleLine)
    .put(EffesParser.CaseExpressionContext.class, ExpressionCompiler::caseExpression)
    .build(ExpressionCompiler::exprLineErr);

  private Expression singleLine(EffesParser.SingleLineExpressionContext ctx) {
    return apply(ctx.expr());
  }

  private Expression caseExpression(EffesParser.CaseExpressionContext ctx) {
    Expression matchAgainst = apply(ctx.expr());
    List<Expression.CaseExpression.CasePattern> patterns =
      ctx.caseExprs().caseExprPattern().stream().map(this::casePattern).collect(Collectors.toList());
    return new Expression.CaseExpression(ctx.getStart(), matchAgainst, patterns);
  }

  private Expression exprLineErr(EffesParser.ExprLineContext ctx) {
    return new Expression.UnrecognizedExpression(ctx.getStart());
  }

  private Expression.CaseExpression.CasePattern casePattern(EffesParser.CaseExprPatternContext ctx) {
    CaseMatcher caseMatcher = caseMatchDispatcher.apply(this, ctx.caseMatcher());
    Expression ifMatches = apply(ctx.exprBlock().expr());
    return new Expression.CaseExpression.CasePattern(caseMatcher, ifMatches);
  }

  private static final Dispatcher<ExpressionCompiler, EffesParser.CaseMatcherContext, CaseMatcher> caseMatchDispatcher =
    Dispatcher.builder(ExpressionCompiler.class, EffesParser.CaseMatcherContext.class, CaseMatcher.class)
      .put(EffesParser.SimpleCtorMatchContext.class, ExpressionCompiler::simpleCtorMatcher)
      .build(ExpressionCompiler::unknownMatcher);

  private CaseMatcher simpleCtorMatcher(EffesParser.SimpleCtorMatchContext ctx) {
    EfType.SimpleType type = typeRegistry.getSimpleType(ctx.TYPE_NAME().getText());
    if (type == null) {
      TerminalNode tok = ctx.TYPE_NAME();
      errs.add(tok.getSymbol(), String.format("unrecognized type '%s' for pattern matcher", tok.getText()));
      return CaseMatcher.ErrorMatch.instance;
    }
    return new CaseMatcher.SimpleCtorMatch(type);
  }

  private CaseMatcher unknownMatcher(EffesParser.CaseMatcherContext ctx) {
    errs.add(ctx.getStart(), "unrecognized case pattern");
    return CaseMatcher.ErrorMatch.instance;
  }
}

package com.yuvalshavit.effes.compile;

import com.google.common.collect.ImmutableList;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.util.Dispatcher;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
      .put(EffesParser.MethodInvokeOrVarExprContext.class, ExpressionCompiler::methodInvokeOrVar)
      .put(EffesParser.ParenExprContext.class, ExpressionCompiler::paren)
      .put(EffesParser.CtorInvokeContext.class, ExpressionCompiler::ctorInvoke)
      .build(ExpressionCompiler::error);

  private Expression error(EffesParser.ExprContext ctx) {
    errs.add(ctx.getStart(), "unrecognized expression");
    return new Expression.UnrecognizedExpression(ctx.getStart());
  }

  private Expression methodInvokeOrVar(EffesParser.MethodInvokeOrVarExprContext ctx) {
    EffesParser.MethodInvokeContext methodInvoke = ctx.methodInvoke();
    // If this method is a VAR_NAME with no args, it could be a var. That takes precedence.
    if (couldBeVar(methodInvoke)) {
      EfVar var = vars.get(methodInvoke.methodName().VAR_NAME().getText());
      if (var != null) {
        return varExpr(var, methodInvoke.methodName().VAR_NAME());
      }
    }
    return methodInvoke(ctx.methodInvoke());
  }

  private static boolean couldBeVar(EffesParser.MethodInvokeContext methodInvoke) {
    return methodInvoke.methodInvokeArgs().expr().isEmpty() && methodInvoke.methodName().VAR_NAME() != null;
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
        String msgFormat = couldBeVar(ctx)
          ? "no such method or variable: '%s'"
          : "no such method: '%s'";
        errs.add(ctx.methodName().getStart(), String.format(msgFormat, methodName));
      }
      isBuiltIn = true; // if method is null, this won't matter
    }

    List<Expression> invokeArgs = ctx.methodInvokeArgs().expr().stream().map(this::apply).collect(Collectors.toList());
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
    List<Expression> args;
    if (type == null) {
      errs.add(ctx.getStart(), "unknown type: " + typeName);
      args = ImmutableList.of();
    }else {
      args = ctx.expr().stream().map(this::apply).collect(Collectors.toList());
    }
    return new Expression.CtorInvoke(ctx.getStart(), type, args);
  }

  private Expression varExpr(EfVar var, TerminalNode name) {
    return new Expression.VarExpression(name.getSymbol(), var);
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
    List<Expression.CaseExpression.CaseAlternative> patterns =
      ctx.caseExprs().caseAlternative().stream().map(this::caseAlternative).filter(Objects::nonNull).collect(Collectors.toList());
    return new Expression.CaseExpression(ctx.getStart(), matchAgainst, patterns);
  }

  private Expression exprLineErr(EffesParser.ExprLineContext ctx) {
    return new Expression.UnrecognizedExpression(ctx.getStart());
  }

  @Nullable
  private Expression.CaseExpression.CaseAlternative caseAlternative(EffesParser.CaseAlternativeContext ctx) {
    TerminalNode tok = ctx.casePattern().TYPE_NAME();
    EfType.SimpleType matchType = typeRegistry.getSimpleType(tok.getText());
    if (matchType == null) {
      errs.add(tok.getSymbol(), String.format("unrecognized type '%s' for pattern matcher", tok.getText()));
      return null;
    }
    // TODO intellij doesn't like the binding, how about javac?
    List<TerminalNode> bindingTokens = ctx.casePattern().VAR_NAME();
    List<EfVar> matchtypeArgs = matchType.getArgs();
    vars.pushScope();
    List<EfVar> bindingArgs = new ArrayList<>(bindingTokens.size());
    for (int i = 0; i < bindingTokens.size(); ++i) {
      TerminalNode bindingToken = bindingTokens.get(i);
      String bindingName = bindingToken.getText();
      EfType bindingType = i < matchtypeArgs.size()
        ? matchtypeArgs.get(i).getType()
        : EfType.UNKNOWN;
      EfVar binding = EfVar.var(bindingName, vars.countElems(), bindingType);
      vars.add(binding, bindingToken.getSymbol());
      bindingArgs.add(binding);
    }
    Expression ifMatches = ctx.exprBlock() != null
      ? apply(ctx.exprBlock().expr())
      : new Expression.UnrecognizedExpression(ctx.getStart());
    vars.popScope();
    return new Expression.CaseExpression.CaseAlternative(matchType, bindingArgs, ifMatches);
  }
}

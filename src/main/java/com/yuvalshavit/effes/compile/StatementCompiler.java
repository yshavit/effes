package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.util.Dispatcher;
import org.antlr.v4.runtime.Token;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class StatementCompiler implements Function<EffesParser.StatContext, Statement> {

  private final ExpressionCompiler expressionCompiler;
  private final MethodsRegistry<?> methodsRegistry;
  private final MethodsRegistry<?> builtInMethods;
  private final CompileErrors errs;
  private final Scopes<EfVar, Token> vars;

  public StatementCompiler(ExpressionCompiler expressionCompiler,
                           MethodsRegistry<?> methodsRegistry,
                           MethodsRegistry<?> builtInMethods,
                           CompileErrors errs,
                           Scopes<EfVar, Token> vars) {
    this.expressionCompiler = expressionCompiler;
    this.methodsRegistry = methodsRegistry;
    this.builtInMethods = builtInMethods;
    this.errs = errs;
    this.vars = vars;
  }

  @Override
  public Statement apply(EffesParser.StatContext statContext) {
    return dispatcher.apply(this, statContext);
  }

  static final Dispatcher<StatementCompiler, EffesParser.StatContext, Statement> dispatcher =
    Dispatcher.builder(StatementCompiler.class, EffesParser.StatContext.class, Statement.class)
      .put(EffesParser.AssignStatContext.class, StatementCompiler::assignStatement)
      .put(EffesParser.MethodInvokeContext.class, StatementCompiler::methodInvoke)
      .put(EffesParser.ReturnStatContext.class, StatementCompiler::returnStat)
      .build();

  public static final java.util.concurrent.atomic.AtomicBoolean sawOneAssignment = new AtomicBoolean(false);
  private Statement assignStatement(EffesParser.AssignStatContext ctx) {
    if (!sawOneAssignment.compareAndSet(false, true))
      throw new IllegalStateException("Effes only supports one variable for now! Globally!");
    Expression value = expressionCompiler.apply(ctx.exprLine());
    int pos = 0; // TODO
    EfVar var = EfVar.arg(ctx.VAR_NAME().getText(), pos, value.resultType());
    vars.add(var, ctx.VAR_NAME().getSymbol());
    return new Statement.AssignStatement(ctx.getStart(), var, value);
  }

  private Statement methodInvoke(EffesParser.MethodInvokeContext ctx) {
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
      .map(expressionCompiler::apply).collect(Collectors.toList());
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

    return new Statement.MethodInvoke(ctx.getStart(), methodName, invokeArgs, resultType, isBuiltIn);
  }

  private Statement returnStat(EffesParser.ReturnStatContext ctx) {
    Expression expr = expressionCompiler.apply(ctx.exprLine());
    return new Statement.ReturnStatement(ctx.exprLine().getStart(), expr);
  }
}

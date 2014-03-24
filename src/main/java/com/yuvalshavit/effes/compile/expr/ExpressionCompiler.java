package com.yuvalshavit.effes.compile.expr;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.yuvalshavit.effes.base.Type;
import com.yuvalshavit.effes.compile.CompileException;
import com.yuvalshavit.effes.base.BuiltIns;
import com.yuvalshavit.effes.ir.Expression;
import com.yuvalshavit.effes.parser.EffesParser;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class ExpressionCompiler {

  public Expression run(Type declaringContext, EffesParser.ExprContext expr) {
    return new DispatchImpl(declaringContext).createExpr(expr);
  }

  public Expression run(Type declaringContext, EffesParser.ExprLineContext exprLine) {
    EffesParser.ExprContext expr = exprLine.expr();
    if (expr != null) {
      return run(declaringContext, expr);
    } else {
      return new DispatchImpl(declaringContext).createExpr(exprLine.multilineExpr());
    }
  }

  private static class DispatchImpl implements Dispatch, MultilineDispatch {
    private final Type declaringContext;

    private DispatchImpl(Type declaringContext) {
      this.declaringContext = declaringContext;
    }

    @Override
    public Expression unary(EffesParser.UnaryExprContext ctx) {
      // turn into "zero OP expr"
      String op = ctx.ADD_OPS().getText();
      Expression expr = createExpr(ctx.expr());
      Expression zero = methodInvokeExpr(expr, "zero");
      return methodInvokeExpr(zero, op, expr);
    }

    @Override
    public Expression methodInvoke(EffesParser.MethodInvokeContext ctx) {
      List<Expression> args = ctx.methodInvokeArgs()
        .expr()
        .stream()
        .map(this::createExpr)
        .collect(Collectors.toList());
      Expression target = createExpr(ctx.target);
      String methodName = ctx.methodName().getText();
      return methodInvokeExpr(target, methodName, args);
    }

    public Expression methodInvokeExpr(Expression target, String method, Expression... args) {
      return methodInvokeExpr(target, method, ImmutableList.copyOf(args));
    }

    private Expression methodInvokeExpr(Expression target, String method, List<Expression> args) {
      return new Expression.MethodExpressionStub(method, target, ImmutableList.copyOf(args));
    }

    @Override
    public Expression intLiteral(EffesParser.IntLiteralContext ctx) {
      return new Expression.FloatLiteralExpression(ctx.INT().getText());
    }

    @Override
    public Expression decimalLiteral(EffesParser.DecimalLiteralContext ctx) {
      return new Expression.FloatLiteralExpression(ctx.DECIMAL().getText());
    }

    @Override
    public Expression ctor(EffesParser.CtorInvokeContext ctx) {
      String typeName = ctx.TYPE_NAME().getText();
      Type.SimpleType type = null;
      assert false : "look up type"; // TODO
      List<Expression> children = createExprs(ctx.ctorInvokeArgs().expr());
      return new Expression.ConstructExpression(type, children);
    }

    @Override
    public Expression tuple(EffesParser.TupleExprContext ctx) {
      List<Expression> exprs = ctx.expr().stream().map(this::createExpr).collect(Collectors.toList());
      return new Expression.TupleExpression(ImmutableList.copyOf(exprs));
    }

    @Override
    public Expression methodWithThis(EffesParser.ThisMethodInvokeContext ctx) {
      String method = ctx.methodName().getText();
      List<Expression> args = ctx.methodInvokeArgs().expr().stream().map(this::createExpr).collect(Collectors.toList());
      Expression declaringExpr = new Expression.ThisExpr(declaringContext);
      return methodInvokeExpr(declaringExpr, method, args);
    }

    @Override
    public Expression var(EffesParser.VarExprContext ctx) {
      String name = ctx.VAR_NAME().getText();
      return new Expression.VarExpr(null, name);
    }

    @Override
    public Expression leftCompose(EffesParser.LeftComposeContext ctx) {
      throw new UnsupportedOperationException(); // TODO
    }

    @Override
    public Expression addOrSub(EffesParser.AddOrSubExprContext ctx) {
      Expression left = createExpr(ctx.expr(0));
      Expression right = createExpr(ctx.expr(1));
      return methodInvokeExpr(left, ctx.ADD_OPS().getText(), right);
    }

    @Override
    public Expression multOrDiv(EffesParser.MultOrDivExprContext ctx) {
      Expression left = createExpr(ctx.expr(0));
      Expression right = createExpr(ctx.expr(1));
      return methodInvokeExpr(left, ctx.MULT_OPS().getText(), right);
    }

    @Override
    public Expression componentCast(EffesParser.ComponentCastExprContext ctx) {
      throw new UnsupportedOperationException(); // TODO
    }

    @Override
    public Expression compare(EffesParser.CompareExprContext ctx) {
      Expression left = createExpr(ctx.expr(0));
      Expression right = createExpr(ctx.expr(1));
      Expression cmp = methodInvokeExpr(left, "cmp", right);
      if (!Objects.equals(cmp.getResultType(), BuiltIns.comparisonType)) {
        throw new CompileException("invalid cmp method for type " + left.getResultType(), ctx.CMP_OPS().getSymbol());
      }
      final List<? extends Type> lookingFor;
      switch (ctx.CMP_OPS().getText()) {
      case "<":
        lookingFor = ImmutableList.of(BuiltIns.ltType);
        break;
      case ">":
        lookingFor = ImmutableList.of(BuiltIns.gtType);
        break;
      case "<=":
        lookingFor = ImmutableList.of(BuiltIns.ltType, BuiltIns.eqType);
        break;
      case ">=":
        lookingFor = ImmutableList.of(BuiltIns.gtType, BuiltIns.eqType);
        break;
      case "==":
        lookingFor = ImmutableList.of(BuiltIns.eqType);
        break;
      case "!=":
        lookingFor = ImmutableList.of(BuiltIns.ltType, BuiltIns.gtType);
        break;
      default:
        throw new AssertionError("unrecognized comparison op: " + ctx.CMP_OPS().getText());
      }
      return new Expression.VarTypeOfExpression(cmp, lookingFor);
    }

    @Override
    public Expression ifElse(EffesParser.IfExprContext ctx) {
      return new Expression.IfElseExpression(createExpr(ctx.cond), createExpr(ctx.trueExpr), createExpr(ctx.falseExpr));
    }

    @Override
    public Expression caseOfExpr(EffesParser.CaseOfExprContext ctx) {
      throw new UnsupportedOperationException(); // TODO
    }
  }

  private static Map<Class<?>, BiFunction<Dispatch, Object, Expression>> createDispatchMap() {
    ImmutableMap.Builder<Class<?>, BiFunction<Dispatch, Object, Expression>> builder = ImmutableMap.builder();
    builder.put(EffesParser.UnaryExprContext.class, (d, o) -> d.unary((EffesParser.UnaryExprContext) o));
    builder.put(EffesParser.MethodInvokeContext.class, (d, o) -> d.methodInvoke((EffesParser.MethodInvokeContext) o));
    builder.put(EffesParser.IntLiteralContext.class, (d, o) -> d.intLiteral((EffesParser.IntLiteralContext)o));
    builder.put(EffesParser.DecimalLiteralContext.class, (d, o) -> d.decimalLiteral((EffesParser.DecimalLiteralContext)o));
    builder.put(EffesParser.CtorInvokeContext.class, (d, o) -> d.ctor((EffesParser.CtorInvokeContext)o));
    builder.put(EffesParser.TupleExprContext.class, (d, o) -> d.tuple((EffesParser.TupleExprContext)o));
    builder.put(EffesParser.ThisMethodInvokeContext.class, (d, o) -> d.methodWithThis((EffesParser.ThisMethodInvokeContext) o));
    builder.put(EffesParser.VarExprContext.class, (d, o) -> d.var((EffesParser.VarExprContext)o));
    builder.put(EffesParser.LeftComposeContext.class, (d, o) -> d.leftCompose((EffesParser.LeftComposeContext)o));
    builder.put(EffesParser.DollarExprContext.class, (d, o) -> d.dollar((EffesParser.DollarExprContext)o));
    builder.put(EffesParser.AddOrSubExprContext.class, (d, o) -> d.addOrSub((EffesParser.AddOrSubExprContext)o));
    builder.put(EffesParser.ParenExprContext.class, (d, o) -> d.paren((EffesParser.ParenExprContext)o));
    builder.put(EffesParser.PipeExprContext.class, (d, o) -> d.pipe((EffesParser.PipeExprContext)o));
    builder.put(EffesParser.MultOrDivExprContext.class, (d, o) -> d.multOrDiv((EffesParser.MultOrDivExprContext)o));
    builder.put(EffesParser.ComponentCastExprContext.class, (d, o) -> d.componentCast((EffesParser.ComponentCastExprContext)o));
    builder.put(EffesParser.CompareExprContext.class, (d, o) -> d.compare((EffesParser.CompareExprContext)o));
    builder.put(EffesParser.IfExprContext.class, (d, o) -> d.ifElse((EffesParser.IfExprContext)o));
    return builder.build();
  }

  interface Dispatch {
    static Map<Class<?>, BiFunction<Dispatch, Object, Expression>> dispatchMap = createDispatchMap();

    default Expression createExpr(EffesParser.ExprContext ctx) {
      BiFunction<Dispatch, Object, Expression> dispatch = dispatchMap.get(ctx.getClass());
      assert dispatch != null: "no dispatch for " + ctx.getClass();
      return dispatch.apply(this, ctx);
    }

    default List<Expression> createExprs(List<EffesParser.ExprContext> exprs) {
      return exprs.stream().map(this::createExpr).collect(Collectors.toList());
    }

    Expression unary(EffesParser.UnaryExprContext ctx);
    Expression methodInvoke(EffesParser.MethodInvokeContext ctx);
    Expression intLiteral(EffesParser.IntLiteralContext ctx);
    Expression decimalLiteral(EffesParser.DecimalLiteralContext ctx);
    Expression ctor(EffesParser.CtorInvokeContext ctx);
    Expression tuple(EffesParser.TupleExprContext ctx);
    Expression methodWithThis(EffesParser.ThisMethodInvokeContext ctx);
    Expression var(EffesParser.VarExprContext ctx);
    Expression leftCompose(EffesParser.LeftComposeContext ctx);
    Expression addOrSub(EffesParser.AddOrSubExprContext ctx);
    Expression multOrDiv(EffesParser.MultOrDivExprContext ctx);
    Expression componentCast(EffesParser.ComponentCastExprContext ctx);
    Expression compare(EffesParser.CompareExprContext ctx);
    Expression ifElse(EffesParser.IfExprContext ctx);

    default Expression dollar(EffesParser.DollarExprContext ctx) {
      return createExpr(ctx.expr());
    }

    default Expression pipe(EffesParser.PipeExprContext ctx) {
      return createExpr(ctx.expr());
    }

    default Expression paren(EffesParser.ParenExprContext ctx) {
      return createExpr(ctx.expr());
    }
  }

  private static Map<Class<?>, BiFunction<MultilineDispatch, Object, Expression>> createMultilineDispatchMap() {
    ImmutableMap.Builder<Class<?>, BiFunction<MultilineDispatch, Object, Expression>> builder = ImmutableMap.builder();
    builder.put(EffesParser.CaseOfExprContext.class, (d, o) -> d.caseOfExpr((EffesParser.CaseOfExprContext) o));
    return builder.build();
  }

  interface MultilineDispatch {
    static Map<Class<?>, BiFunction<MultilineDispatch, Object, Expression>> dispatchMap = createMultilineDispatchMap();

    default Expression createExpr(EffesParser.MultilineExprContext ctx) {
      BiFunction<MultilineDispatch, Object, Expression> dispatch = dispatchMap.get(ctx.getClass());
      assert dispatch != null: "no dispatch for " + ctx.getClass();
      return dispatch.apply(this, ctx);
    }

    Expression caseOfExpr(EffesParser.CaseOfExprContext ctx);
  }
}

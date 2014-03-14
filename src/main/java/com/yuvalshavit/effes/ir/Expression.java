package com.yuvalshavit.effes.ir;

import com.google.common.collect.ImmutableList;
import com.yuvalshavit.effes.base.BuiltIns;
import com.yuvalshavit.effes.base.EfMethodMeta;
import com.yuvalshavit.effes.base.Type;

import javax.annotation.Nullable;
import java.util.List;
import java.util.stream.Collectors;

public abstract class Expression implements EfNode {
  private final Type resultType;

  private Expression(Type resultType) {
    this.resultType = resultType;
  }

  @Nullable
  public Type getResultType() {
    return resultType;
  }

  private abstract static class LeafExpression extends Expression {

    protected LeafExpression(Type resultType) {
      super(resultType);
    }

    @Override
    public ImmutableList<EfNode> children() {
      return ImmutableList.of();
    }
  }

  public static class IntLiteralExpression extends LeafExpression {

    public IntLiteralExpression(String value) {
      super(null);
      throw new UnsupportedOperationException(); // TODO
    }
  }

  public static class FloatLiteralExpression extends LeafExpression {
    private final double value;

    public FloatLiteralExpression(String value) {
      super(BuiltIns.floatType);
      this.value = Double.parseDouble(value); // TODO what if this throws? e.g. double is out of range
    }
  }

  public static class MethodExpressionStub extends Expression {
    private final String method;
    private final Expression target;
    private final ImmutableList<Expression> args;

    public MethodExpressionStub(String method, Expression target, ImmutableList<Expression> args) {
      super(null);
      this.method = method;
      this.target = target;
      this.args = args;
    }

    @Override
    public ImmutableList<? extends EfNode> children() {
      return args;
    }
  }

  public static class MethodExpression extends Expression {
    private final EfMethodMeta method;
    private final Expression target;
    private final ImmutableList<Expression> args;

    public MethodExpression(Type resultType, EfMethodMeta method, Expression target, ImmutableList<Expression> args) {
      super(resultType);
      this.method = method;
      this.target = target;
      this.args = args;
    }

    @Override
    public ImmutableList<? extends EfNode> children() {
      return args;
    }
  }

  public static class TupleExpression extends Expression {
    private final ImmutableList<Expression> exprs;

    public TupleExpression(ImmutableList<Expression> exprs) {
      super(tupleType(exprs));
      this.exprs = exprs;
    }

    private static Type tupleType(List<Expression> exprs) {
      List<Type> types = exprs.stream().map(Expression::getResultType).collect(Collectors.toList());
      return new Type.TupleType(types);
    }

    @Override
    public ImmutableList<? extends EfNode> children() {
      return exprs;
    }
  }

  public static class ThisExpr extends LeafExpression {
    public ThisExpr(Type resultType) {
      super(resultType);
    }
  }

  public static class VarExpr extends LeafExpression {
    private final String name;

    public VarExpr(Type resultType, String name) {
      super(resultType);
      this.name = name;
    }
  }

  public static class VarTypeOfExpression extends LeafExpression {
    private final Expression expr;
    private final List<Type> needles;

    public VarTypeOfExpression(Expression expr, List<? extends Type> needles) {
      super(BuiltIns.booleanType);
      this.expr = expr;
      this.needles = ImmutableList.copyOf(needles);
    }
  }

  public static class IfElseExpression extends Expression {
    private final Expression cond;
    private final Expression ifTrue;
    private final Expression ifFalse;

    public IfElseExpression(Expression cond, Expression ifTrue, Expression ifFalse) {
      super(BuiltIns.booleanType);
      this.cond = cond;
      this.ifTrue = ifTrue;
      this.ifFalse = ifFalse;
    }

    @Override
    public ImmutableList<? extends EfNode> children() {
      return ImmutableList.of(cond, ifTrue, ifFalse);
    }
  }
}

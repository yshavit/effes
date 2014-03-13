package com.yuvalshavit.effes.ir;

import com.google.common.collect.ImmutableList;
import com.yuvalshavit.effes.base.BuiltIns;
import com.yuvalshavit.effes.base.EfMethodMeta;
import com.yuvalshavit.effes.base.EfVariable;
import com.yuvalshavit.effes.base.State;
import com.yuvalshavit.effes.base.Type;
import com.yuvalshavit.effes.base.TypeRegistery;

import java.util.List;
import java.util.stream.Collectors;

public abstract class Expression implements EfNode {
  private final Type resultType;

  private Expression(Type resultType) {
    this.resultType = resultType;
  }

  public Type getResultType() {
    return resultType;
  }

  public final EfVariable evaluate(State state) {
    EfVariable var = doEvaluate(state);
    TypeRegistery reg = state.typeRegistry();
    assert var.getType().isSubtypeOf(resultType) : String.format("%s is not a %s", var.getType(), resultType);
    return var;
  }

  protected abstract EfVariable doEvaluate(State state);

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

    @Override
    public EfVariable doEvaluate(State state) {
      throw new UnsupportedOperationException(); // TODO
    }
  }

  public static class FloatLiteralExpression extends LeafExpression {
    private final double value;

    public FloatLiteralExpression(String value) {
      super(BuiltIns.floatType);
      this.value = Double.parseDouble(value); // TODO what if this throws? e.g. double is out of range
    }

    @Override
    public EfVariable doEvaluate(State state) {
      return new EfVariable(getResultType(), value);
    }
  }

  public static class MethodExpression extends Expression {
    private final EfMethodMeta method;
    private final Expression target;
    private final ImmutableList<Expression> args;

    public MethodExpression(EfMethodMeta method, Expression target, ImmutableList<Expression> args) {
      super(method.getReturnType());
      this.method = method;
      this.target = target;
      this.args = args;
    }

    @Override
    public EfVariable doEvaluate(State state) {
      EfVariable targetVar = target.doEvaluate(state);
      List<EfVariable> argVars = args.stream().map(e -> e.doEvaluate(state)).collect(Collectors.toList());
      assert method.getMethod() != null;
      return method.getMethod().invoke(targetVar, argVars);
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

    @Override
    public EfVariable doEvaluate(State state) {
      List<EfVariable> values = exprs.stream().map(e -> e.doEvaluate(state)).collect(Collectors.toList());
      return new EfVariable(getResultType(), ImmutableList.copyOf(values));
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

    @Override
    public EfVariable doEvaluate(State state) {
      return state.getDeclaringContext();
    }
  }

  public static class VarExpr extends LeafExpression {
    private final String name;

    public VarExpr(Type resultType, String name) {
      super(resultType);
      this.name = name;
    }

    @Override
    public EfVariable doEvaluate(State state) {
      return state.getScopes().lookUp(name);
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

    @Override
    protected EfVariable doEvaluate(State state) {
      EfVariable exprValue = expr.evaluate(state);
      for (Type needle : needles) {
        if (exprValue.getType().isSubtypeOf(needle)) {
          return new EfVariable(BuiltIns.trueType);
        }
      }
      return new EfVariable(BuiltIns.falseType);
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
    protected EfVariable doEvaluate(State state) {
      EfVariable condVar = cond.evaluate(state);
      Expression which = condVar.getType().isSubtypeOf(BuiltIns.trueType)
        ? ifTrue
        : ifFalse;
      return which.evaluate(state);
    }

    @Override
    public ImmutableList<? extends EfNode> children() {
      return ImmutableList.of(cond, ifTrue, ifFalse);
    }
  }
}

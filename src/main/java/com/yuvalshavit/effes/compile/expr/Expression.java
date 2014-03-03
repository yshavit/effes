package com.yuvalshavit.effes.compile.expr;

import com.google.common.collect.ImmutableList;
import com.yuvalshavit.effes.interpreter.BuiltIns;
import com.yuvalshavit.effes.interpreter.EfMethod;
import com.yuvalshavit.effes.interpreter.EfType;
import com.yuvalshavit.effes.interpreter.EfVariable;
import com.yuvalshavit.effes.interpreter.State;
import com.yuvalshavit.effes.interpreter.TupleType;
import com.yuvalshavit.effes.interpreter.TupleVar;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public abstract class Expression {
  private final EfType resultType;

  protected Expression(EfType resultType) {
    this.resultType = resultType;
  }

  public EfType getResultType() {
    return resultType;
  }

  public final EfVariable evaluate(State state) {
    EfVariable var = doEvaluate(state);
    assert var.getType().instanceOf(resultType) : String.format("%s not a %s", var, resultType);
    return var;
  }

  protected abstract EfVariable doEvaluate(State state);

  public static class IntLiteralExpression extends Expression {

    public IntLiteralExpression(String value) {
      super(null);
      throw new UnsupportedOperationException(); // TODO
    }

    @Override
    public EfVariable doEvaluate(State state) {
      throw new UnsupportedOperationException(); // TODO
    }
  }
  public static class DecimalLiteralExpression extends Expression {

    public DecimalLiteralExpression(String value) {
      super(null);
      throw new UnsupportedOperationException(); // TODO
    }

    @Override
    public EfVariable doEvaluate(State state) {
      throw new UnsupportedOperationException(); // TODO
    }
  }

  static class MethodExpression extends Expression {
    private final String methodName;
    private final Expression target;
    private final ImmutableList<Expression> args;

    MethodExpression(EfType resultType, String methodName, Expression target, ImmutableList<Expression> args) {
      super(resultType);
      this.methodName = methodName;
      this.target = target;
      this.args = args;
    }

    @Override
    public EfVariable doEvaluate(State state) {
      EfVariable targetVar = target.doEvaluate(state);
      List<EfVariable> argVars = args.stream().map(e -> e.doEvaluate(state)).collect(Collectors.toList());
      EfMethod method = null; // TODO
      return method.invoke(targetVar, argVars);
    }
  }

  static class TupleExpression extends Expression {
    private final ImmutableList<Expression> exprs;
    TupleExpression(ImmutableList<Expression> exprs) {
      super(tupleType(exprs));
      this.exprs = exprs;
    }

    @Override
    public EfVariable doEvaluate(State state) {
      List<EfVariable> values = exprs.stream().map(e -> e.doEvaluate(state)).collect(Collectors.toList());
      return new TupleVar(getResultType(), ImmutableList.copyOf(values));
    }

    private static EfType tupleType(List<Expression> exprs) {
      List<EfType> types = exprs.stream().map(Expression::getResultType).collect(Collectors.toList());
      return new TupleType(ImmutableList.copyOf(types));
    }
  }

  static class ThisExpr extends Expression {
    ThisExpr(EfType resultType) {
      super(resultType);
    }

    @Override
    public EfVariable doEvaluate(State state) {
      return state.getDeclaringContext();
    }
  }

  static class VarExpr extends Expression {
    private final String name;
    VarExpr(EfType resultType, String name) {
      super(resultType);
      this.name = name;
    }

    @Override
    public EfVariable doEvaluate(State state) {
      return state.getScopes().lookUp(name);
    }
  }

  static class VarTypeOfExpression extends Expression {
    private final Expression expr;
    private final List<EfType> needles;

    VarTypeOfExpression(Expression expr, List<? extends EfType> needles) {
      super(BuiltIns.booleanType);
      this.expr = expr;
      this.needles = ImmutableList.copyOf(needles);
    }

    @Override
    protected EfVariable doEvaluate(State state) {
      EfVariable exprValue = expr.evaluate(state);
      for (EfType needle : needles) {
        if (exprValue.getType().instanceOf(needle)) {
          return BuiltIns.valueTrue;
        }
      }
      return BuiltIns.valueFalse;
    }
  }

  static class IfElseExpression extends Expression {
    private final Expression cond;
    private final Expression ifTrue;
    private final Expression ifFalse;

    IfElseExpression(Expression cond, Expression ifTrue, Expression ifFalse) {
      super(BuiltIns.booleanType);
      this.cond = cond;
      this.ifTrue = ifTrue;
      this.ifFalse = ifFalse;
    }

    @Override
    protected EfVariable doEvaluate(State state) {
      EfVariable condVar = cond.evaluate(state);
      Expression which = condVar.getType().instanceOf(BuiltIns.typeTrue)
        ? ifTrue
        : ifFalse;
      return which.evaluate(state);
    }
  }
}

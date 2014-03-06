package com.yuvalshavit.effes.interpreter;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleBinaryOperator;

public class BuiltIns {
  public static final EfType lt = new EfType();
  public static final EfType gt = new EfType();
  public static final EfType eq = new EfType();
  public static final EfType comparison = EfType.alias(lt, gt, eq);

  public static final EfType typeTrue = new EfType();
  public static final EfType typeFalse = new EfType();
  public static final EfType booleanType = EfType.alias(typeTrue, typeFalse);

  public static final EfVariable valueTrue = new EfVariable(typeTrue, Collections.emptyList());
  public static final EfVariable valueFalse = new EfVariable(typeFalse, Collections.emptyList());

  public static final EfType typeFloat = builder()
    .method("+", doubleArith((l, r) -> l + r), EfMethodMeta.owningType, EfMethodMeta.owningType)
    .method("-", doubleArith((l, r) -> l - r), EfMethodMeta.owningType, EfMethodMeta.owningType)
    .method("*", doubleArith((l, r) -> l * r), EfMethodMeta.owningType, EfMethodMeta.owningType)
    .method("/", doubleArith((l, r) -> l / r), EfMethodMeta.owningType, EfMethodMeta.owningType)
    .build();

  private static EfMethod doubleArith(DoubleBinaryOperator op) {
    return (target, args) -> {
      double targetValue = getOnlyState(target);
      double opValue = getOnlyState(getOnly(args));
      return new EfVariable(target.getType(), op.applyAsDouble(targetValue, opValue));
    };
  }

  public static Builder builder() {
    return new Builder();
  }

  @SuppressWarnings("unchecked")
  private static <T> T getOnlyState(EfVariable var) {
    return (T) getOnly(var.getState());
  }

  private static <T> T getOnly(List<? extends T> list) {
    assert list.size() == 1 : list;
    return list.get(0);
  }

  private static final class Builder {
    public static final Map<String, EfMethodMeta> methods = Maps.newHashMap();

    public Builder method(String name, EfMethod method, EfType returnType, EfType... argTypes) {
      Object old = methods.put(name, new EfMethodMeta(method, returnType, ImmutableList.copyOf(argTypes)));
      assert old == null : name;
      return this;
    }

    public EfType build() {
      return new EfType(methods);
    }
  }
}

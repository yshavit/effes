package com.yuvalshavit.effes.base;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.function.DoubleBinaryOperator;

public class BuiltIns {

  public static final String LT = "Lt";
  public static final String GT = "Gt";
  public static final String EQ = "Eq";
  public static final String COMPARISON = "Comparison";
  public static final String TRUE = "True";
  public static final String FALSE = "False";
  public static final String BOOLEAN = "Boolean";
  public static final String FLOAT = "Float";

  private BuiltIns() {}

  private static final TypeRegistry builtinRegistry = createBuiltinRegistry();

  public static final Type ltType = builtinRegistry.getTypeId(LT);
  public static final Type gtType = builtinRegistry.getTypeId(GT);
  public static final Type eqType = builtinRegistry.getTypeId(EQ);
  public static final Type comparisonType = builtinRegistry.getTypeId(COMPARISON);
  public static final Type trueType = builtinRegistry.getTypeId(TRUE);
  public static final Type falseType = builtinRegistry.getTypeId(FALSE);
  public static final Type booleanType = builtinRegistry.getTypeId(BOOLEAN);
  public static final Type floatType = builtinRegistry.getTypeId(FLOAT);

  public static TypeRegistry createRegistry() {
    return new TypeRegistryBuilder(builtinRegistry);
  }

  private static TypeRegistry createBuiltinRegistry() {
    TypeRegistryBuilder reg = new TypeRegistryBuilder(TypeRegistry.EMPTY);

    Type.SimpleType lt = reg.register(LT);
    Type.SimpleType gt = reg.register(GT);
    Type.SimpleType eq = reg.register(EQ);
    Type.SimpleType comparison = reg.register(COMPARISON);
    reg.registerSubtype(comparison, lt);
    reg.registerSubtype(comparison, gt);
    reg.registerSubtype(comparison, eq);

    Type.SimpleType trueType = reg.register(TRUE);
    Type.SimpleType falseType = reg.register(FALSE);
    Type.SimpleType booleanType = reg.register(BOOLEAN);
    reg.registerSubtype(booleanType, trueType);
    reg.registerSubtype(booleanType, falseType);
    reg.registerMethod(booleanType, "not", booleanType, ImmutableList.of(), null);
    EfVariable trueVar = new EfVariable(trueType);
    EfVariable falseVar = new EfVariable(falseType);
    reg.registerMethod(trueType, "not", falseType, ImmutableList.of(), (t, a) -> trueVar);
    reg.registerMethod(falseType, "not", trueType, ImmutableList.of(), (t, a) -> falseVar);

    Type floatType = reg.register(FLOAT);
    ImmutableList<Type> oneFloat = ImmutableList.of(floatType);
    reg.registerMethod(floatType, "+", floatType, oneFloat, doubleArith((l, r) -> l + r));
    reg.registerMethod(floatType, "-", floatType, oneFloat, doubleArith((l, r) -> l - r));
    reg.registerMethod(floatType, "*", floatType, oneFloat, doubleArith((l, r) -> l * r));
    reg.registerMethod(floatType, "/", floatType, oneFloat, doubleArith((l, r) -> l / r));

    return reg;
  }

  private static EfMethod doubleArith(DoubleBinaryOperator op) {
    return (target, args) -> {
      double targetValue = getOnlyState(target);
      double opValue = getOnlyState(getOnly(args));
      return new EfVariable(target.getType(), op.applyAsDouble(targetValue, opValue));
    };
  }

  @SuppressWarnings("unchecked")
  private static <T> T getOnlyState(EfVariable var) {
    return (T) getOnly(var.getState());
  }

  private static <T> T getOnly(List<? extends T> list) {
    assert list.size() == 1 : list;
    return list.get(0);
  }
}

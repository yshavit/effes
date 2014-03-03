package com.yuvalshavit.effes.interpreter;

public class BuiltIns {
  public static final EfType lt = new EfType();
  public static final EfType gt = new EfType();
  public static final EfType eq = new EfType();
  public static final EfType comparison = EfType.alias(lt, gt, eq);

  public static final EfType typeTrue = new EfType();
  public static final EfType typeFalse = new EfType();
  public static final EfType booleanType = EfType.alias(typeTrue, typeFalse);

  public static final EfVariable valueTrue = new EfVariable(typeTrue);
  public static final EfVariable valueFalse = new EfVariable(typeFalse);
}

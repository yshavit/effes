package com.yuvalshavit.effes.compile.node;

import java.util.Collections;

/**
 * Built-in types. These all have non-standard enum names to make it easier to look them up from effes code.
 * Each enum name corresponds exactly to its effes type.
 */
public enum BuiltinType {
  IntZero("IntZero", false),
  IntValue("IntValue", true),
  
  String("String", true),
  ;
  
  private final EfType.SimpleType efType;
  private final boolean hasLargeDomain;

  BuiltinType(String efTypeName, boolean hasLargeDomain) {
    this.hasLargeDomain = hasLargeDomain;
    this.efType = new EfType.SimpleType(efTypeName, Collections.<String>emptyList());
  }

  public static EfType.SimpleType efTypeFor(long value) {
    return value == 0
      ? IntZero.getEfType()
      : IntValue.getEfType();
  }

  public EfType.SimpleType getEfType() {
    return efType;
  }

  public static boolean isBuiltinWithLargeDomain(EfType.SimpleType type) {
    for (BuiltinType builtIn : BuiltinType.values()) {
      if (builtIn.efType.equals(type)) {
        return builtIn.hasLargeDomain;
      }
    }
    return false;
  }
}

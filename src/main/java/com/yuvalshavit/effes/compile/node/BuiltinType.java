package com.yuvalshavit.effes.compile.node;

import java.util.Collections;

/**
 * Built-in types. These all have non-standard enum names to make it easier to look them up from effes code.
 * Each enum name corresponds exactly to its effes type.
 */
public enum BuiltinType {
  IntZero("IntZero"),
  IntValue("IntValue"),
  
  String("String"),
  ;
  
  private final EfType.SimpleType efType;

  BuiltinType(String efTypeName) {
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
}

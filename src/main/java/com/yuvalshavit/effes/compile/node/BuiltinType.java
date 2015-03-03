package com.yuvalshavit.effes.compile.node;

import java.util.Collections;

public enum BuiltinType {
  INT_ZERO("IntZero"),
  INT_VALUE("IntValue"),
  
  STRING("String"),
  ;
  
  private final EfType.SimpleType efType;

  BuiltinType(String efTypeName) {
    this.efType = new EfType.SimpleType(efTypeName, Collections.<String>emptyList());
  }

  public static EfType.SimpleType efTypeFor(long value) {
    return value == 0
      ? INT_ZERO.getEfType()
      : INT_VALUE.getEfType();
  }

  public EfType.SimpleType getEfType() {
    return efType;
  }
}

package com.yuvalshavit.effes.compile.node;

import java.util.Collections;

public enum BuiltinTypes {
  INT_ZERO("IntZero"),
  INT_VALUE("IntValue"),
  ;
  
  private final EfType.SimpleType efType;

  BuiltinTypes(String efTypeName) {
    this.efType = new EfType.SimpleType(efTypeName, Collections.<String>emptyList());
  }

  public EfType.SimpleType getEfType() {
    return efType;
  }
}

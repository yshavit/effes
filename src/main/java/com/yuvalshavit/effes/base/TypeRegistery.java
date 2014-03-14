package com.yuvalshavit.effes.base;

import javax.annotation.Nullable;

public interface TypeRegistery {
  Type.SimpleType getTypeId(String name);
  @Nullable EfMethodMeta getMethod(Type type, String methodName);

  boolean isSubtype(Type.SimpleType potentialSupertype, Type.SimpleType potentialSubtype);

  public static final TypeRegistery EMPTY = new TypeRegistery() {
    @Override
    public Type.SimpleType getTypeId(String name) {
      return null;
    }

    @Nullable
    @Override
    public EfMethodMeta getMethod(Type type, String methodName) {
      return null;
    }

    @Override
    public boolean isSubtype(Type.SimpleType potentialSupertype, Type.SimpleType potentialSubtype) {
      return false;
    }
  };
}

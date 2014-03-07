package com.yuvalshavit.effes.base;

import javax.annotation.Nullable;

public interface TypeRegistery {
  Type.SimpleType getTypeId(String name);
  @Nullable EfMethodMeta getMethod(Type type, String methodName);

  boolean isSubtype(Type.SimpleType potentialSupertype, Type.SimpleType potentialSubtype);
}

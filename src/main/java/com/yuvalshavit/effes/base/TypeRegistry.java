package com.yuvalshavit.effes.base;

import javax.annotation.Nullable;
import java.util.List;

public interface TypeRegistry {
  Type.SimpleType getTypeId(String name);
  @Nullable EfMethodMeta getMethod(Type type, String methodName);
  boolean isSubtype(Type.SimpleType potentialSupertype, Type.SimpleType potentialSubtype);
  
  Type.SimpleType register(String name);
  void registerSubtype(Type.SimpleType supertype, Type.SimpleType subtype);
  void registerMethod(Type target, String name, Type returnType, List<Type> argTypes, @Nullable EfMethod method);

  boolean isFrozen();
  void freeze();

  public static final TypeRegistry EMPTY = new TypeRegistry() {

    @Override
    public Type.SimpleType register(String name) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void registerSubtype(Type.SimpleType supertype, Type.SimpleType subtype) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void registerMethod(Type target, String name, Type returnType, List<Type> argTypes,
                               @Nullable EfMethod method) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void freeze() {
      // nothing
    }

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

    @Override
    public boolean isFrozen() {
      return true;
    }
  };
}

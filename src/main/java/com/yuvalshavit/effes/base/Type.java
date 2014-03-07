package com.yuvalshavit.effes.base;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import java.util.List;

public abstract class Type {
  private Type() {}
  public abstract boolean isSubtypeOf(Type other);
  @Nullable public abstract EfMethodMeta getMethod(String method);

  public static class SimpleType extends Type {
    private final TypeRegistery registery;
    private final String name;

    public SimpleType(TypeRegistery registery, String name) {
      this.registery = registery;
      this.name = name;
    }

    @Override
    @Nullable
    public EfMethodMeta getMethod(String method) {
      return registery.getMethod(this, method);
    }

    @Override
    public boolean isSubtypeOf(Type other) {
      if (other instanceof SimpleType) {
        return registery.isSubtype((SimpleType)other, this);
      }
      return false;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  public static class TupleType extends Type {
    private final List<Type> types;

    public TupleType(List<Type> types) {
      this.types = ImmutableList.copyOf(types);
    }

    @Override
    public EfMethodMeta getMethod(String method) {
      throw new UnsupportedOperationException(); // TODO
    }

    @Override
    public boolean isSubtypeOf(Type other) {
      throw new UnsupportedOperationException(); // TODO
    }

    @Override
    public String toString() {
      return "(" + Joiner.on(", ").join(types) + ")";
    }
  }
}

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
      // TODO disjunctive/conjunctive types
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
      return null;
    }

    @Override
    public boolean isSubtypeOf(Type other) {
      if (other instanceof TupleType) {
        // check that this[i].isSubtypeOf(other[i]) for i <= 0 < len
        List<Type> otherTypes = ((TupleType) other).types;
        int nTypes = types.size();
        if (otherTypes.size() == nTypes) {
          for (int i = 0; i < nTypes; ++i) {
            if (!types.get(i).isSubtypeOf(otherTypes.get(i))) {
              return false;
            }
          }
          return true;
        }
      }
      return false;
    }

    @Override
    public String toString() {
      return "(" + Joiner.on(", ").join(types) + ")";
    }
  }
}

package com.yuvalshavit.effes.base;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public final class TypeRegistryBuilder implements TypeRegistery {
  private final BiMap<String, Type.SimpleType> types = HashBiMap.create();
  private final Multimap<Type, Type.SimpleType> subtypesBySuper = HashMultimap.create();
  private final Map<MethodId, EfMethodMeta> methods = Maps.newHashMap();

  @Override
  public Type.SimpleType getTypeId(String name) {
    return types.get(name);
  }

  @Override
  @Nullable
  public EfMethodMeta getMethod(Type type, String methodName) {
    return methods.get(new MethodId(type, methodName));
  }

  @Override
  public boolean isSubtype(Type.SimpleType potentialSupertype, Type.SimpleType potentialSubtype) {
    return subtypesBySuper.containsEntry(potentialSupertype, potentialSubtype);
  }

  public Type.SimpleType register(String name) {
    if (types.containsKey(name)) {
      throw new TypeRegistrationException("duplicate type name: " + name);
    }
    Type.SimpleType newType = new Type.SimpleType(this, name);
    types.put(name, newType);
    return newType;
  }

  public void registerSubtype(Type.SimpleType supertype, Type.SimpleType subtype) {
    if (isSubtype(subtype, supertype)) {
      String msg = String.format("cyclical subtyping: %s is already a subtype of %s", supertype, subtype);
      throw new TypeRegistrationException(msg);
    }
    subtypesBySuper.put(supertype, subtype);
  }

  public void registerMethod(Type target, String name, Type returnType, List<Type> argTypes,
                             @Nullable EfMethod method)
  {
    MethodId methodId = new MethodId(target, name);
    if (methods.containsKey(methodId)) {
      throw new TypeRegistrationException("duplicate method " + name);
    }
    methods.put(methodId, new EfMethodMeta(method, returnType, ImmutableList.copyOf(argTypes)));
  }

  private static class MethodId {
    private final Type target;
    private final String name;

    private MethodId(Type target, String methodName) {
      this.target = target;
      this.name = methodName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MethodId methodId = (MethodId) o;
      return name.equals(methodId.name) && target.equals(methodId.target);

    }

    @Override
    public int hashCode() {
      int result = target.hashCode();
      result = 31 * result + name.hashCode();
      return result;
    }
  }
}

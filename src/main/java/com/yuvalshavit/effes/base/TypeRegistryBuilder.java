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
import java.util.function.BiFunction;

public final class TypeRegistryBuilder implements MutableTypeRegistry {
  private final BiMap<String, Type.SimpleType> types = HashBiMap.create();
  private final Multimap<Type, Type.SimpleType> subtypesBySuper = HashMultimap.create();
  private final Map<MethodId, EfMethodMeta> methods = Maps.newHashMap();
  private final TypeRegistery parent;

  TypeRegistryBuilder(TypeRegistery parent) {
    this.parent = parent;
  }

  @Override
  public Type.SimpleType getTypeId(String name) {
    Type.SimpleType r = parent.getTypeId(name);
    if (r == null) {
      r = types.get(name);
    }
    return r;
  }

  @Override
  @Nullable
  public EfMethodMeta getMethod(Type type, String methodName) {
    BiFunction<Type, String, MethodId> ctor = MethodId::new;
    return firstNonNull(type, methodName, parent::getMethod, ctor.andThen(methods::get));
  }

  @Override
  public boolean isSubtype(Type.SimpleType potentialSupertype, Type.SimpleType potentialSubtype) {
    return firstNonNull(potentialSubtype, potentialSubtype, parent::isSubtype, subtypesBySuper::containsEntry);
  }

  @Override
  public Type.SimpleType register(String name) {
    if (types.containsKey(name) || parent.getTypeId(name) != null) {
      throw new TypeRegistrationException("duplicate type name: " + name);
    }
    Type.SimpleType newType = new Type.SimpleType(this, name);
    types.put(name, newType);
    return newType;
  }

  @Override
  public void registerSubtype(Type.SimpleType supertype, Type.SimpleType subtype) {
    if (isSubtype(subtype, supertype) || parent.isSubtype(subtype, supertype)) {
      String msg = String.format("cyclical subtyping: %s is already a subtype of %s", supertype, subtype);
      throw new TypeRegistrationException(msg);
    }
    subtypesBySuper.put(supertype, subtype);
  }

  @Override
  public void registerMethod(Type target, String name, Type returnType, List<Type> argTypes,
                             @Nullable EfMethod method)
  {
    MethodId methodId = new MethodId(target, name);
    if (methods.containsKey(methodId) || parent.getMethod(target, name) != null) {
      throw new TypeRegistrationException("duplicate method " + name);
    }
    methods.put(methodId, new EfMethodMeta(method, returnType, ImmutableList.copyOf(argTypes)));
  }

  private static <T, U, R> R firstNonNull(T arg1, U arg2, BiFunction<T, U, R> f1, BiFunction<T, U, R> f2) {
    R result = f1.apply(arg1, arg2);
    if (result == null) {
      result = f2.apply(arg1, arg2);
    }
    return result;
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

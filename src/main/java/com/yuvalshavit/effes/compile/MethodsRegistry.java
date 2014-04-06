package com.yuvalshavit.effes.compile;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class MethodsRegistry<B> {
  private final Map<MethodId, EfMethod<? extends B>> methods;

  public MethodsRegistry() {
    this(new HashMap<>());
  }

  private MethodsRegistry(Map<MethodId, EfMethod<? extends B>> methods) {
    this.methods = methods;
  }

  public void registerMethod(MethodId methodId, EfMethod<? extends B> method) {
    if (methods.containsKey(methodId)) {
      throw new DuplicateMethodNameException(methodId);
    }
    methods.put(methodId, method);
  }

  public Collection<? extends EfMethod<? extends B>> getMethods() {
    return Collections.unmodifiableCollection(methods.values());
  }

  public Map<? extends MethodId, ? extends EfMethod<? extends B>> getMethodsByName() {
    return Collections.unmodifiableMap(methods);
  }

  @Nullable
  public EfMethod<? extends B> getMethod(MethodId methodId) {
    return methods.get(methodId);
  }

  public <B2> void addAll(MethodsRegistry<B2> other, Function<? super EfMethod<? extends B2>, ? extends B> transform) {
    for (Map.Entry<MethodId, EfMethod<? extends B2>> otherEntry : other.methods.entrySet()) {
      MethodId otherName = otherEntry.getKey();
      if (methods.containsKey(otherName)) {
        throw new DuplicateMethodNameException(otherName);
      }
      registerMethod(otherName, otherEntry.getValue().tranform(transform));
    }
  }

  public <B2> MethodsRegistry<B2> transform(Function<? super EfMethod<? extends B>, ? extends B2> func) {
    Map<MethodId, EfMethod<? extends B2>> transformedMethods = Maps.transformValues(methods, m -> m.tranform(func));

    transformedMethods = new HashMap<>(transformedMethods); // force and make mutable
    return new MethodsRegistry<>(transformedMethods);
  }

  @VisibleForTesting
  Set<MethodId> getMethodIds() {
    return Collections.unmodifiableSet(methods.keySet());
  }
}

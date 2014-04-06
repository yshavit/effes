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
  private final Map<MethodId, EfMethod<? extends B>> topLevelMethods;

  public MethodsRegistry() {
    this(new HashMap<>());
  }

  private MethodsRegistry(Map<MethodId, EfMethod<? extends B>> topLevelMethods) {
    this.topLevelMethods = topLevelMethods;
  }

  public void registerTopLevelMethod(MethodId methodId, EfMethod<? extends B> method) {
    if (topLevelMethods.containsKey(methodId)) {
      throw new DuplicateMethodNameException(methodId);
    }
    topLevelMethods.put(methodId, method);
  }

  public Collection<? extends EfMethod<? extends B>> getTopLevelMethods() {
    return Collections.unmodifiableCollection(topLevelMethods.values());
  }

  public Map<? extends MethodId, ? extends EfMethod<? extends B>> getTopLevelMethodsByName() {
    return Collections.unmodifiableMap(topLevelMethods);
  }

  @Nullable
  public EfMethod<? extends B> getMethod(MethodId methodId) {
    return topLevelMethods.get(methodId);
  }

  public <B2> void addAll(MethodsRegistry<B2> other, Function<? super EfMethod<? extends B2>, ? extends B> transform) {
    for (Map.Entry<MethodId, EfMethod<? extends B2>> otherEntry : other.topLevelMethods.entrySet()) {
      MethodId otherName = otherEntry.getKey();
      if (topLevelMethods.containsKey(otherName)) {
        throw new DuplicateMethodNameException(otherName);
      }
      registerTopLevelMethod(otherName, otherEntry.getValue().tranform(transform));
    }
  }

  public <B2> MethodsRegistry<B2> transform(Function<? super EfMethod<? extends B>, ? extends B2> func) {
    Map<MethodId, EfMethod<? extends B2>> transformedMethods = Maps.transformValues(topLevelMethods, m -> m.tranform(func));

    transformedMethods = new HashMap<>(transformedMethods); // force and make mutable
    return new MethodsRegistry<>(transformedMethods);
  }

  @VisibleForTesting
  Set<MethodId> getAllTopLevelMethodNames() {
    return Collections.unmodifiableSet(topLevelMethods.keySet());
  }
}

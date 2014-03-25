package com.yuvalshavit.effes.compile;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class MethodsRegistry<B> {
  private final Map<String, EfMethod<B>> topLevelMethods;

  public MethodsRegistry() {
    this(new HashMap<>());
  }

  private MethodsRegistry(Map<String, EfMethod<B>> topLevelMethods) {
    this.topLevelMethods = topLevelMethods;
  }

  public void registerTopLevelMethod(String name, EfMethod<B> method) {
    if (topLevelMethods.containsKey(name)) {
      throw new MethodRegistrationException("duplicate top-level method: " + name);
    }
    topLevelMethods.put(name, method);
  }

  @Nullable
  public EfMethod<B> getMethod(String name) {
    return topLevelMethods.get(name);
  }

  public <B2> void addAll(MethodsRegistry<B2> other, Function<B2, B> transform) {
    for (Map.Entry<String, EfMethod<B2>> otherEntry : other.topLevelMethods.entrySet()) {
      String otherName = otherEntry.getKey();
      if (topLevelMethods.containsKey(otherName)) {
        throw new IllegalArgumentException("duplicate method name: " + otherName);
      }
      registerTopLevelMethod(otherName, otherEntry.getValue().tranform(transform));
    }
  }

  public <B2> MethodsRegistry<B2> transform(Function<B, B2> func) {
    Map<String, EfMethod<B2>> transformedMethods = Maps.transformValues(topLevelMethods, m -> m.tranform(func));
    return new MethodsRegistry<>(new HashMap<>(transformedMethods));
  }

  public MethodsRegistry<Block> compileMethods(Function<B, Block> func) {
    MethodsRegistry<Block> compiled = transform(func);
    for (EfMethod<Block> method : compiled.topLevelMethods.values()) {
      method.getBody().validate(method.getResultType());
    }
    return compiled;
  }

  @VisibleForTesting
  Set<String> getAllTopLevelMethodNames() {
    return Collections.unmodifiableSet(topLevelMethods.keySet());
  }
}

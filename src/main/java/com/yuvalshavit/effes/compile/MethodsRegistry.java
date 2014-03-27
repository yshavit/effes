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
  private final Map<String, EfMethod<? extends B>> topLevelMethods;

  public MethodsRegistry() {
    this(new HashMap<>());
  }

  private MethodsRegistry(Map<String, EfMethod<? extends B>> topLevelMethods) {
    this.topLevelMethods = topLevelMethods;
  }

  public void registerTopLevelMethod(String name, EfMethod<? extends B> method) {
    if (topLevelMethods.containsKey(name)) {
      throw new DuplicateMethodNameException(name);
    }
    topLevelMethods.put(name, method);
  }

  @Nullable
  public EfMethod<? extends B> getMethod(String name) {
    return topLevelMethods.get(name);
  }

  public <B2> void addAll(MethodsRegistry<B2> other, Function<? super EfMethod<? extends B2>, ? extends B> transform) {
    for (Map.Entry<String, EfMethod<? extends B2>> otherEntry : other.topLevelMethods.entrySet()) {
      String otherName = otherEntry.getKey();
      if (topLevelMethods.containsKey(otherName)) {
        throw new DuplicateMethodNameException(otherName);
      }
      registerTopLevelMethod(otherName, otherEntry.getValue().tranform(transform));
    }
  }

  public <B2> MethodsRegistry<B2> transform(Function<? super EfMethod<? extends B>, ? extends B2> func) {
    Map<String, EfMethod<? extends B2>> transformedMethods = Maps.transformValues(topLevelMethods, m -> m.tranform(func));

    transformedMethods = new HashMap<>(transformedMethods); // force and make mutable
    return new MethodsRegistry<>(transformedMethods);
  }

  public MethodsRegistry<Block> compileMethods(Function<EfMethod<? extends B>, Block> func, CompileErrors errs) {
    MethodsRegistry<Block> compiled = transform(func);
    for (EfMethod<? extends Block> method : compiled.topLevelMethods.values()) {
      method.getBody().validate(method.getResultType(), errs);
    }
    return compiled;
  }

  @VisibleForTesting
  Set<String> getAllTopLevelMethodNames() {
    return Collections.unmodifiableSet(topLevelMethods.keySet());
  }
}

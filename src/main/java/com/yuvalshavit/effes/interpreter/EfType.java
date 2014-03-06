package com.yuvalshavit.effes.interpreter;

import com.google.common.collect.ImmutableMap;

import java.util.Collections;
import java.util.Map;

public class EfType {
  private final Map<String, EfMethodMeta> methods;

  public static EfType alias(EfType... alternatives) {
    throw new UnsupportedOperationException(); // TODO
  }

  public EfType() {
    this(Collections.emptyMap());
  }

  public EfType(Map<String, EfMethodMeta> methods) {
    this.methods = ImmutableMap.copyOf(methods);
  }

  public Map<String, EfMethodMeta> getMethods() {
    return methods;
  }

  public boolean instanceOf(EfType other) {
    throw new UnsupportedOperationException(); // TODO
  }
}

package com.yuvalshavit.effes.compile;

import com.google.common.annotations.VisibleForTesting;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class TypeRegistry {

  private final Map<String, SimpleType> simpleTypes = new HashMap<>();

  public SimpleType registerType(String name) {
    if (simpleTypes.containsKey(name)) {
      throw new TypeRegistryException("duplicate type name: " + name);
    }
    SimpleType r = new SimpleType(name);
    simpleTypes.put(name, r);
    return r;
  }

  @VisibleForTesting
  public Set<String> getAllSimpleTypeNames() {
    return Collections.unmodifiableSet(simpleTypes.keySet());
  }

  public static class TypeRegistryException extends RuntimeException {
    public TypeRegistryException(String message) {
      super(message);
    }
  }
}

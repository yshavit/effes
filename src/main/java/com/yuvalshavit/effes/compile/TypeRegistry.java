package com.yuvalshavit.effes.compile;

import com.google.common.annotations.VisibleForTesting;
import org.antlr.v4.runtime.Token;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class TypeRegistry {

  private final Map<String, SimpleType> simpleTypes = new HashMap<>();

  public void registerType(Token token, String name) {
    if (simpleTypes.containsKey(name)) {
      throw new TypeRegistryException(token, "duplicate type name: " + name);
    }
    SimpleType r = new SimpleType(name);
    simpleTypes.put(name, r);
  }

  @VisibleForTesting
  public Set<String> getAllSimpleTypeNames() {
    return Collections.unmodifiableSet(simpleTypes.keySet());
  }

  @Nullable
  public SimpleType getSimpleType(String name) {
    return simpleTypes.get(name);
  }

}

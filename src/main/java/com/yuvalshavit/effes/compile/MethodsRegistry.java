package com.yuvalshavit.effes.compile;

import com.google.common.annotations.VisibleForTesting;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class MethodsRegistry {
  private final Map<String, EfMethod> topLevelMethods = new HashMap<>();

  public void registerTopLevelMethod(String name, EfMethod method) {
    if (topLevelMethods.containsKey(name)) {
      throw new MethodRegistrationException("duplicate top-level method: " + name);
    }
    topLevelMethods.put(name, method);
  }

  @VisibleForTesting
  public Set<String> getAllTopLevelMethodNames() {
    return Collections.unmodifiableSet(topLevelMethods.keySet());
  }

}

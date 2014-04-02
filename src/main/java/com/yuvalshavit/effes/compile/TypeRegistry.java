package com.yuvalshavit.effes.compile;

import com.google.common.annotations.VisibleForTesting;
import org.antlr.v4.runtime.Token;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TypeRegistry {

  private final Map<String, EfType.SimpleType> simpleTypes = new HashMap<>();
  private final CompileErrors errs;

  public TypeRegistry(CompileErrors errs) {
    this.errs = errs;
  }

  public void registerType(Token token, String name) {
    if (simpleTypes.containsKey(name)) {
      errs.add(token, "duplicate type name: " + name);
    }
    EfType.SimpleType r = new EfType.SimpleType(name);
    simpleTypes.put(name, r);
  }

  @VisibleForTesting
  public Set<String> getAllSimpleTypeNames() {
    return Collections.unmodifiableSet(simpleTypes.keySet());
  }

  @Nullable
  public EfType.SimpleType getSimpleType(String name) {
    return simpleTypes.get(name);
  }

}

package com.yuvalshavit.effes.compile;

import com.google.common.annotations.VisibleForTesting;
import com.yuvalshavit.effes.compile.node.CompileErrors;
import com.yuvalshavit.effes.compile.node.EfType;
import org.antlr.v4.runtime.Token;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class TypeRegistry {

  private final Map<String, EfType.SimpleType> simpleTypes = new HashMap<>();
  private final Map<String, EfType> typeAliases = new HashMap<>();
  private final CompileErrors errs;

  public TypeRegistry(CompileErrors errs) {
    this.errs = errs;
  }

  public void registerAlias(Token token, String name, EfType type) {
    if (nameIsAvailable(token, name)) {
      typeAliases.put(name,  type);
    }
  }

  @Nullable
  public EfType.SimpleType registerType(Token token, String name) {
    if (nameIsAvailable(token, name)) {
      EfType.SimpleType r = new EfType.SimpleType(name);
      simpleTypes.put(name, r);
      return r;
    } else {
      return null;
    }
  }

  @VisibleForTesting
  public Set<String> getAllSimpleTypeNames() {
    return Collections.unmodifiableSet(simpleTypes.keySet());
  }

  @Nullable
  public EfType.SimpleType getSimpleType(String name) {
    return simpleTypes.get(name);
  }

  @Nullable
  public EfType getType(String name) {
    EfType type = getSimpleType(name);
    return type != null
      ? type
      : typeAliases.get(name);
  }

  private boolean nameIsAvailable(Token token, String name) {
    boolean taken = simpleTypes.containsKey(name) || typeAliases.containsKey(name);
    if (taken) {
      errs.add(token, "duplicate type name: " + name);
    }
    return ! taken;
  }
}

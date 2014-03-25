package com.yuvalshavit.effes;

import com.yuvalshavit.effes.compile.SimpleType;
import com.yuvalshavit.effes.compile.TypeRegistry;

import static org.testng.Assert.assertNotNull;

public final class TUtils {
  private TUtils() {}

  public static SimpleType getExistingType(TypeRegistry registry, String name) {
    SimpleType type = registry.getSimpleType(name);
    assertNotNull(type, name);
    return type;
  }

  public static TypeRegistry typeRegistry(String... types) {
    TypeRegistry registry = new TypeRegistry();
    for (String type : types) {
      registry.registerType(type);
    }
    return registry;
  }
}
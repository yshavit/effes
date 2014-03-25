package com.yuvalshavit.effes.compile;

import static org.testng.Assert.assertNotNull;

final class TUtils {
  private TUtils() {}

  static SimpleType getExistingType(TypeRegistry registry, String name) {
    SimpleType type = registry.getSimpleType(name);
    assertNotNull(type, name);
    return type;
  }

  static TypeRegistry typeRegistry(String... types) {
    TypeRegistry registry = new TypeRegistry();
    for (String type : types) {
      registry.registerType(type);
    }
    return registry;
  }
}

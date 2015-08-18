package com.yuvalshavit.effes;

import com.yuvalshavit.effes.compile.node.CompileErrors;
import com.yuvalshavit.effes.compile.TypeRegistry;
import com.yuvalshavit.effes.compile.node.EfType;

import java.util.Collections;
import java.util.function.Consumer;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public final class TUtils {
  private TUtils() {}

  public static TypeRegistry typeRegistry(String... types) {
    TypeRegistry registry = new TypeRegistry(new CompileErrors());
    for (String type : types) {
      registry.registerType(null, type, Collections.emptyList());
      EfType.SimpleType createdType = registry.getSimpleType(type);
      assertNotNull(createdType, type);
      createdType.setCtorArgs(Collections.emptyList());
    }
    return registry;
  }

  public static void expectNoErrors(Consumer<CompileErrors> action) {
    CompileErrors errs = new CompileErrors();
    action.accept(errs);
    assertFalse(errs.hasErrors());
  }

  public static void expectErrors(Consumer<CompileErrors> action) {
    CompileErrors errs = new CompileErrors();
    expectErrors(errs, () -> action.accept(errs));
  }

  public static void expectErrors(CompileErrors errs, Runnable action) {
    assertFalse(errs.hasErrors());
    action.run();
    assertTrue(errs.hasErrors());
  }
}

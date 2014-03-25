package com.yuvalshavit.effes.base;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

import java.util.function.Function;

import static com.yuvalshavit.util.AssertException.assertException;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public final class TypeRegistryBuilderTest {

  @DataProvider(name="t1")
  public static Object[][] dataProvider() {
    Function<TypeRegistryBuilder, TypeRegistryBuilder> createChild = t -> {
      t.freeze();
      return new TypeRegistryBuilder(t);
    };
    return new Object[][] {
      new Object[] { Function.identity() }, // solo
      new Object[] { createChild }
    };
  }

  private final Function<TypeRegistryBuilder, TypeRegistryBuilder> regTransform;

  @Factory(dataProvider="t1")
  public TypeRegistryBuilderTest(Function<TypeRegistryBuilder, TypeRegistryBuilder> regTransform) {
    this.regTransform = regTransform;
  }

  @Test
  public void directSubtype() {
    TypeRegistryBuilder reg = new TypeRegistryBuilder();
    Type.SimpleType number = reg.register("Number");
    Type.SimpleType integer = reg.register("Integer");
    reg.registerSubtype(number, integer);

    TypeRegistryBuilder c = regTransform.apply(reg);
    assertTrue(c.isSubtype(number, integer));
    assertFalse(c.isSubtype(integer, number));
  }


  @Test
  public void indirectSubtype() {
    TypeRegistryBuilder reg = new TypeRegistryBuilder();
    Type.SimpleType number = reg.register("Number");
    Type.SimpleType rational = reg.register("Rational");
    Type.SimpleType integer = reg.register("Integer");

    reg.registerSubtype(number, rational);
    reg.registerSubtype(rational, integer);

    TypeRegistryBuilder c = regTransform.apply(reg);
    assertTrue(c.isSubtype(number, integer));
    assertFalse(c.isSubtype(integer, number));
  }

  @Test
  public void childRegistersSubtypesFromParent() {
    TypeRegistryBuilder parent = new TypeRegistryBuilder();
    Type.SimpleType number = parent.register("Number");
    Type.SimpleType integer = parent.register("Integer");
    parent.freeze();
    TypeRegistryBuilder child = new TypeRegistryBuilder(parent);

    child.registerSubtype(number, integer);

    assertTrue(child.isSubtype(number, integer));
    assertFalse(child.isSubtype(integer, number));
  }

  @Test
  public void directSubtypeCycle() {
    TypeRegistryBuilder reg = new TypeRegistryBuilder();
    Type.SimpleType number = reg.register("Number");
    Type.SimpleType integer = reg.register("Integer");
    reg.registerSubtype(number, integer);
    TypeRegistryBuilder c = regTransform.apply(reg);
    assertException(TypeRegistrationException.class, () -> c.registerSubtype(integer, number));
  }

  @Test
  public void indirectSubtypeCycle() {
    TypeRegistryBuilder reg = new TypeRegistryBuilder();
    Type.SimpleType number = reg.register("Number");
    Type.SimpleType rational = reg.register("Rational");
    Type.SimpleType integer = reg.register("Integer");

    reg.registerSubtype(number, rational);
    reg.registerSubtype(rational, integer);

    TypeRegistryBuilder c = regTransform.apply(reg);
    assertException(TypeRegistrationException.class, () -> c.registerSubtype(integer, number));
  }

  @Test
  public void registerSameTypeTwice() {
    TypeRegistryBuilder reg = new TypeRegistryBuilder();
    reg.register("Foo");
    TypeRegistryBuilder c = regTransform.apply(reg);
    assertException(TypeRegistrationException.class, () -> c.register("Foo"));
  }

  @Test
  public void getSameInstanceAfterRegistering() {
    TypeRegistryBuilder reg = new TypeRegistryBuilder();
    Type.SimpleType original = reg.register("Foo");
    Type.SimpleType second = reg.getTypeId("Foo");
    assertSame(original, second);
  }

  @Test
  public void registerAfterFreezing() {
    TypeRegistryBuilder reg = new TypeRegistryBuilder();
    reg.freeze();
    assertException(IllegalStateException.class, () -> reg.register("Foo"));
  }

  @Test
  public void registerSubtypeAfterFreezing() {
    TypeRegistryBuilder reg = new TypeRegistryBuilder();
    Type.SimpleType number = reg.register("Number");
    Type.SimpleType integer = reg.register("Integer");
    reg.freeze();
    assertException(IllegalStateException.class, () -> reg.registerSubtype(number, integer));
  }

  // TODO method registration
}

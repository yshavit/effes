package com.yuvalshavit.util;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class EqualityTest {
  final JustAName onlyBob1 = new JustAName("Bob");
  final JustAName onlyBob2 = new JustAName("Bob");
  final JustAName onlyJohn = new JustAName("John");
  final NameAndBool bobTrue1 = new NameAndBool("Bob", true);
  final NameAndBool bobTrue2 = new NameAndBool("Bob", true);
  final NameAndBool bobFalse = new NameAndBool("Bob", false);
  final JustAName nullName = new JustAName(null);

  final Equality<Named> byNamed = Equality.forClass(Named.class).with(Named::name).includingSubclasses();
  final Equality<NameAndBool> byNameForNameAndBool = Equality.forClass(NameAndBool.class)
    .with(Named::name)
    .exactClassOnly();
  final Equality<NameAndBool> byAllForNameAndBool = Equality.forClass(NameAndBool.class)
    .with(Named::name)
    .with(o -> o.value)
    .exactClassOnly();

  @Test
  public void byNamedEquality() {
    assertTrue(byNamed.areEqual(onlyBob1, onlyBob1));
    assertTrue(byNamed.areEqual(onlyBob1, onlyBob2));
    assertTrue(byNamed.areEqual(onlyBob1, bobTrue1));
    assertTrue(byNamed.areEqual(bobTrue1, bobFalse));

    assertFalse(byNamed.areEqual(onlyBob1, onlyJohn));
    assertFalse(byNamed.areEqual(onlyBob1, "John"));
  }

  @Test
  public void equalityToNulls() {
    assertFalse(byNamed.areEqual(onlyBob1, null));
    assertFalse(byNamed.areEqual(null, onlyBob1));
    assertTrue(byNamed.areEqual(null, null));
  }

  @Test
  public void stateIncludesNullsEquality() {
    assertTrue(byNamed.areEqual(nullName, nullName));
  }

  @Test
  public void stateIncludesNullsHash() {
    assertEquals(byNamed.hash(nullName), byNamed.hash(nullName));
  }

  @Test
  public void byNamedHash() {
    assertEquals(byNamed.hash(onlyBob1), byNamed.hash(onlyBob1));
    assertEquals(byNamed.hash(onlyBob1), byNamed.hash(onlyBob2));
    assertEquals(byNamed.hash(onlyBob1), byNamed.hash(bobTrue1));
    assertEquals(byNamed.hash(bobTrue1), byNamed.hash(bobFalse));

    assertNotEquals(byNamed.hash(onlyBob1), byNamed.hash(onlyJohn));
  }

  @Test
  public void byNamedButWithoutSubclasses() {
    Equality<Named> byNamedNoSubclass = Equality.forClass(Named.class).with(Named::name).exactClassOnly();
    assertTrue(byNamedNoSubclass.areEqual(onlyBob1, onlyBob1));
    assertFalse(byNamedNoSubclass.areEqual(onlyBob1, onlyBob2));
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void equalityOnIllegalSubclass() {
    Equality<Named> byNamedNoSubclass = Equality.forClass(Named.class).with(Named::name).exactClassOnly();
    byNamedNoSubclass.hash(onlyBob1);
  }

  @Test
  public void byNameEqualityForNameAndBool() {
    assertTrue(byNameForNameAndBool.areEqual(bobTrue1, bobTrue1));
    assertTrue(byNameForNameAndBool.areEqual(bobTrue1, bobTrue2));
    assertTrue(byNameForNameAndBool.areEqual(bobTrue1, bobFalse));
  }

  @Test
  public void byAllEqualityForNameAndBool() {
    assertTrue(byAllForNameAndBool.areEqual(bobTrue1, bobTrue1));
    assertTrue(byAllForNameAndBool.areEqual(bobTrue1, bobTrue2));
    assertFalse(byAllForNameAndBool.areEqual(bobTrue1, bobFalse));
  }

  @Test
  public void instanceEquality() {
    assertTrue(Equality.instance(bobTrue1).with(NameAndBool::name).with(n -> n.value).isEqualTo(bobTrue2));
    assertFalse(Equality.instance(bobTrue1).with(NameAndBool::name).with(n -> n.value).isEqualTo(bobFalse));
  }

  @Test
  public void instanceHash() {
    int bobTrue1Hash = Equality.instance(bobTrue1).with(NameAndBool::name).with(n -> n.value).hash();
    int bobTrue2Hash = Equality.instance(bobTrue2).with(NameAndBool::name).with(n -> n.value).hash();
    assertEquals(bobTrue1Hash, bobTrue2Hash);
  }

  @Test(expectedExceptions = ClassCastException.class)
  @SuppressWarnings("unchecked")
  public void byNamedHashWithWrongCast() {
    Object o = "hello";
    Equality raw = byNamed;
    raw.hash(o);
  }

  private interface Named {
    String name();
  }

  private static class NameAndBool implements Named {
    private final String name;
    private final boolean value;

    public NameAndBool(String name, boolean value) {
      this.name = name;
      this.value = value;
    }

    @Override
    public String name() {
      return name;
    }
  }

  private static class JustAName implements Named {
    private final String name;

    public JustAName(String name) {
      this.name = name;
    }

    @Override
    public String name() {
      return name;
    }
  }

}
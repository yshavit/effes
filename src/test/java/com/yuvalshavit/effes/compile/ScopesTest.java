package com.yuvalshavit.effes.compile;

import org.testng.annotations.Test;

import static com.yuvalshavit.util.AssertException.assertException;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public final class ScopesTest {
  @Test
  public void popEmpty() {
    Scopes<Elem, Id> scopes = create();
    assertEquals(scopes.depth(), 0);
    assertException(IllegalStateException.class, scopes::popScope);
  }

  @Test
  public void noLookupOnEmpty() {
    Scopes<Elem, Id> scopes = create();
    assertException(IllegalStateException.class, () -> scopes.get("whatever"));
  }

  @Test
  public void singleScope() {
    Scopes<Elem, Id> scopes = create();
    assertEquals(scopes.depth(), 0);

    scopes.pushScope();
    assertEquals(scopes.depth(), 1);
    Elem one = elem("one");
    Elem two = elem("two");
    scopes.add(one, id(1));
    scopes.add(two, id(2));
    assertEquals(scopes.get("one"), one);
    assertEquals(scopes.get("two"), two);
    assertNull(scopes.get("three"));

    scopes.popScope();
    assertEquals(scopes.depth(), 0);
  }

  @Test
  public void scopeCloser() {
    Scopes<Elem, Id> scopes = create();
    assertEquals(scopes.depth(), 0);

    try (Scopes.ScopeCloser ignored = scopes.pushScope()) {
      assertEquals(scopes.depth(), 1);
      Elem one = elem("one");
      scopes.add(one, id(1));
      assertEquals(scopes.get("one"), one);
    }

    assertEquals(scopes.depth(), 0);
  }

  @Test
  public void duplicate() {
    Scopes<Elem, Id> scopes = create();

    scopes.pushScope();
    Elem one = elem("one");
    scopes.add(one, id(1));
    assertException(DuplicateElemException.class, () -> scopes.add(elem("one"), id(2)));
    // check that the old one didn't get bumped out
    assertEquals(scopes.get("one"), one);
  }

  @Test
  public void twoScopes() {
    Scopes<Elem, Id> scopes = create();
    assertEquals(scopes.depth(), 0);

    scopes.pushScope();
    assertEquals(scopes.depth(), 1);
    Elem aOne = elem("one", "a");
    Elem aTwo = elem("two", "a");
    scopes.add(aOne, id(1));
    scopes.add(aTwo, id(2));
    assertEquals(scopes.get("one"), aOne);
    assertEquals(scopes.get("two"), aTwo);

    scopes.pushScope();
    assertEquals(scopes.depth(), 2);
    Elem bOne = elem("one", "b");
    assertException(DuplicateElemException.class, () -> scopes.add(bOne, id(3))); // shadows aOne
    assertEquals(scopes.get("one"), aOne);
    assertEquals(scopes.get("two"), aTwo);

    scopes.popScope();
    assertEquals(scopes.depth(), 1);
    assertEquals(scopes.get("one"), aOne);
    assertEquals(scopes.get("two"), aTwo);

    scopes.popScope();
    assertEquals(scopes.depth(), 0);
    assertException(IllegalStateException.class, scopes::popScope);
  }

  @Test
  public void shadowInDifferentScopes() {
    Scopes<Elem, Id> scopes = create();

    Elem aOne = elem("one", "a");
    Elem bOne = elem("one", "b");

    scopes.pushScope();
    scopes.add(aOne, id(1));
    assertEquals(scopes.get("one"), aOne);

    scopes.pushScope();
    scopes.shadow(bOne);
    assertEquals(scopes.get("one"), bOne);

    scopes.popScope();
    assertEquals(scopes.get("one"), aOne);
  }

  @Test
  public void shadowUnknownVar() {
    Scopes<Elem, Id> scopes = create();

    Elem aOne = elem("one", "a");
    Elem bTwo = elem("two", "b");

    scopes.pushScope();
    scopes.add(aOne, id(1));
    assertEquals(scopes.get("one"), aOne);

    scopes.pushScope();
    assertException(IllegalArgumentException.class, () -> scopes.shadow(bTwo));
  }

  @Test
  public void shadowInSameScope() {
    Scopes<Elem, Id> scopes = create();

    Elem aOne = elem("one", "a");
    Elem bOne = elem("one", "b");

    scopes.pushScope();
    scopes.add(aOne, id(1));
    assertEquals(scopes.get("one"), aOne);

    assertException(IllegalArgumentException.class, () -> scopes.shadow(bOne));
    assertEquals(scopes.get("one"), aOne);
  }

  private static Scopes<Elem, Id> create() {
    return new Scopes<>(e -> e.key, (e, ignored) -> { throw new DuplicateElemException(e); });
  }

  private static Elem elem(String key, String name) {
    return new Elem(key, name);
  }

  private static Elem elem(String key) {
    return new Elem(key, null);
  }

  private static class Elem {
    private final String key;
    private final String group;

    private Elem(String key, String group) {
      this.key = key;
      this.group = group;
    }

    @Override
    public String toString() {
      return group != null
        ? String.format("%s (%s)", key, group)
        : key;
    }
  }

  private static class DuplicateElemException extends RuntimeException {
    private DuplicateElemException(String key) {
      super(key);
    }
  }

  // Id is just to get something nice and short

  private static Id id(int value) {
    return new Id(value);
  }

  private static class Id {
    private final int value;

    private Id(int value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return Integer.toString(value);
    }
  }
}

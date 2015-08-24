package com.yuvalshavit.util;

import static org.testng.Assert.*;

import java.util.Objects;
import java.util.function.Supplier;

import org.testng.annotations.Test;

public class LazyTest {
  @Test
  public void hashPreservesLaziness() {
    CountingSupplier supplier = new CountingSupplier("foo");
    Lazy<String> lazy = Lazy.lazy(supplier);
    assertTrue(lazy.isUnforced(), "lazy should be lazy");
    assertEquals(supplier.accessCount, 0);
    assertEquals(lazy.hashCode(), Objects.hash("foo"));
    assertTrue(lazy.isUnforced(), "lazy should be lazy");
    assertEquals(supplier.accessCount, 1);
  }

  @Test
  public void equalityPreservesLaziness() {
    CountingSupplier supplier = new CountingSupplier("foo");
    Lazy<String> first = Lazy.lazy(supplier);
    Lazy<String> second = Lazy.forced("foo");
    assertEquals(first, second);
    assertTrue(first.isUnforced(), "first should still be lazy");
    assertFalse(second.isUnforced(), "second should not be lazy");
    assertEquals(supplier.accessCount, 1);
  }

  @Test
  public void getWithoutForcing() {
    CountingSupplier supplier = new CountingSupplier("foo");
    Lazy<String> lazy = Lazy.lazy(supplier);
    
    lazy.getWithoutForcing();
    assertEquals(supplier.accessCount, 1);
    assertTrue(lazy.isUnforced(), "lazy should be lazy");
    
    lazy.getWithoutForcing();
    assertEquals(supplier.accessCount, 1);
    assertTrue(lazy.isUnforced(), "lazy should still be lazy");

    lazy.get();
    assertFalse(lazy.isUnforced(), "lazy should be forced");
    assertEquals(supplier.accessCount, 1);
  }
  
  private static class CountingSupplier implements Supplier<String> {
    final String value;
    int accessCount;

    public CountingSupplier(String value) {
      this.value = value;
    }

    @Override
    public String get() {
      ++accessCount;
      return value;
    }
  }
}

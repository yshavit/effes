package com.yuvalshavit.util;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.function.Supplier;

/**
 * A supplier that delegates to some other supplier just once; subsequent invocations will just return that
 * same instance. This is like guava's memoizing Supplier, but not thread safe (because I don't need it to be) and
 * with a better toString (which is really the reason I don't want to use guava's).
 * @param <T>
 */
public class Lazy<T> implements Supplier<T> {
  private Supplier<? extends T> unforced;
  private T forced;

  public static <T> Lazy<T> lazy(Supplier<? extends T> supplier) {
    return new Lazy<>(supplier);
  }

  public static <T> Lazy<T> forced(T elem) {
    return new Lazy<>(elem);
  }
  
  private Lazy(T value) {
    forced = value;
  }
  
  private Lazy(Supplier<? extends T> delegate) {
    this.unforced = checkNotNull(delegate);
  }

  @Override
  public T get() {
    if (isUnforced()) {
      forced = unforced.get();
      unforced = null;
    }
    return forced;
  }
  
  public boolean isUnforced() {
    return unforced != null;
  }

  @Override
  public String toString() {
    if (unforced == null) {
      return String.valueOf(forced);
    } else {
      return "...";
    }
  }
}

package com.yuvalshavit.util;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A supplier that delegates to some other supplier just once; subsequent invocations will just return that
 * same instance. This is like guava's memoizing Supplier, but not thread safe (because I don't need it to be) and
 * with a better toString (which is really the reason I don't want to use guava's).
 * @param <T>
 */
public class Lazy<T> implements Supplier<T> {
  
  private static final Equality<Lazy> equality = Equality.forClass(Lazy.class).with("value", Lazy::getWithoutForcing).exactClassOnly();
  
  public static final String UNFORCED_DESC = "...";
  private Supplier<? extends T> unforced;
  private T forced;
  private final int id;
  private static final AtomicInteger ids = new AtomicInteger();

  public static <T> Lazy<T> lazy(Supplier<? extends T> supplier) {
    if (supplier instanceof Lazy) {
      @SuppressWarnings("unchecked")
      Lazy<T> ibid = (Lazy<T>) supplier; // its type safety is as good as the input's
      return ibid;
    }
    return new Lazy<>(supplier);
  }

  public static <T> Lazy<T> forced(T elem) {
    return new Lazy<>(elem);
  }
  
  private Lazy(T value) {
    forced = value;
    id = assignId();
  }
  
  private Lazy(Supplier<? extends T> delegate) {
    this.unforced = checkNotNull(delegate);
    id = assignId();
  }

  private int assignId() {
    return ids.getAndIncrement();
  }

  public <R> Lazy<R> transform(Function<? super T, ? extends R> transformation) {
    return isUnforced()
      ? lazy(() -> transformation.apply(get()))
      : forced(transformation.apply(get()));
  }

  /**
   * <p>Gets the lazy value, without making this instance appear forced if it wasn't before. If this instance was already forced, this behaves exactly like
   * {@code get()}.</p>
   * 
   * <p>Of course, it <em>will</em> be forced, in the sense of the underlying supplier having been called. But it will not appear to be in any other sense.
   * Specifically, {@code isUnforced} will still return {@code false}, and {@code toString} will not show the value.</p>
   * 
   * <p>One way in which this <em>will</em> seem forced: the underlying supplier's value will be cached, so the supplier will not be invoked again.</p>
   * 
   * <p>The primary use case is to get the value, e.g. for testing or debugging, while maintaining lazy behavior that functionality or usability may require.
   * For instance, a test may want to check that a given instance of Lazy has not been forced.</p>
   * @return the value to supply
   */
  public T getWithoutForcing() {
    final T value;
    if (isUnforced()) {
      value = unforced.get();
      unforced = Lazy.forced(value);
    } else {
      value = forced;
    }
    return value;
  }

  @Override
  public T get() {
    if (isUnforced()) {
      forced = unforced.get();
      unforced = null;
    }
    return forced;
  }
  
  public boolean isForced() {
    return unforced == null;
  }
  
  public boolean isUnforced() {
    return unforced != null;
  }

  @Override
  public String toString() {
    if (unforced == null) {
      return String.valueOf(forced);
    } else {
      return UNFORCED_DESC;
    }
  }

  /**
   * Derives the hash from the underlying supplier's value, which is obtained via {@link #getWithoutForcing()}
   */
  @Override
  public int hashCode() {
    return equality.hash(this);
  }

  /**
   * Derives equality from the underlying supplier's value, which is obtained via {@link #getWithoutForcing()}
   */
  @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
  @Override
  public boolean equals(Object obj) {
    return equality.areEqual(this, obj);
  }

  public int getId() {
    return id;
  }
}

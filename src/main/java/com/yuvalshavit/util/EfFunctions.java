package com.yuvalshavit.util;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class EfFunctions {
  private EfFunctions() {}
  
  public static <T> Supplier<T> throwingSupplier() {
    return () -> { throw new UnsupportedOperationException(); };
  }
  
  public static <T> Consumer<T> throwingConsumer() {
    return t -> { throw new UnsupportedOperationException(); };
  }
  
  public static <T, R> Function<T, R> throwingFunction() {
    return t -> { throw new UnsupportedOperationException(); };
  }
  
  public static <T, R> Function<T, R> fromGuava(com.google.common.base.Function<? super T, ? extends R> f) {
    return f::apply;
  }
  
  public static <T> Function<? super T, ? extends Void> toFunction(Consumer<? super T> consumer) {
    return t -> {
      consumer.accept(t);
      return null;
    };
  }
  
  public static <T, R> BiFunction<? super T, ? super R, ? extends Void> toFunction(BiConsumer<? super T, ? super R> consumer) {
    return (t, r) -> {
      consumer.accept(t, r);
      return null;
    };
  }

  /**
   * <p>Returns a Function that takes an input and checks it against some type. If the input is an instance of that type, the result is a single-element Stream
   * of that input cast as that type; otherwise, the result is an empty stream.</p>
   * 
   * <p>This is intended to be used in flatMap. For instance, to get a stream of uppercased Strings out of a stream of Objects, you could do:</p>
   * 
   * <pre>
   *   objectStream.flatMap(instancesOf(String.class)).map(String::toUpperCase);
   * </pre>
   */
  public static <I, O extends I> Function<I, Stream<O>> instancesOf(Class<O> clazz) {
    return input -> clazz.isInstance(input)
      ? Stream.of(clazz.cast(input))
      : Stream.empty();
  }
}

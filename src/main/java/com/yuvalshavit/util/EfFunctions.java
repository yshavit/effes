package com.yuvalshavit.util;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;

public class EfFunctions {
  private EfFunctions() {}
  
  public static <T> Consumer<? super T> emptyConsumer() {
    return v -> { /*nothing*/ };
  }
  
  public static <T, R> Function<T, R> fromGuava(com.google.common.base.Function<? super T, ? extends R> f) {
    return f::apply;
  }
  
  public static <T> Function<? super T, ? extends Void> from(Consumer<? super T> consumer) {
    return t -> {
      consumer.accept(t);
      return null;
    };
  }
}

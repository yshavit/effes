package com.yuvalshavit.util;

import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;

public class EfFunctions {
  private EfFunctions() {}
  
  public static <T> Supplier<T> memoizing(Supplier<T> supplier) {
    return Suppliers.memoize(supplier::get)::get;
  }
  
  public static <T, R> Function<T, R> fromGuava(com.google.common.base.Function<? super T, ? extends R> f) {
    return f::apply;
  }
}

package com.yuvalshavit.util;

import java.util.function.Supplier;

import com.google.common.base.Suppliers;

public class EfFunctions {
  private EfFunctions() {}
  
  public static <T> Supplier<T> memoizing(Supplier<T> supplier) {
    return Suppliers.memoize(supplier::get)::get;
  }
}

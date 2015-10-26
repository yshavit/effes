package com.yuvalshavit.util;

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

public interface Mapping<T, R> extends Function<T, R> {
  Map<R, T> mappings();
  
  static <T> Mapping<T, String> onToString() {
    return new Mapping<T, String>() {
      @Override
      public Map<String, T> mappings() {
        return Collections.emptyMap();
      }

      @Override
      public String apply(T t) {
        return String.valueOf(t);
      }
    };
  }
}

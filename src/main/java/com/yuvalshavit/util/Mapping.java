package com.yuvalshavit.util;

import java.util.Map;
import java.util.function.Function;

public interface Mapping<T, R> extends Function<T, R> {
  Map<R, T> mappings();
}

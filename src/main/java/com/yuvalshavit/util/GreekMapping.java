package com.yuvalshavit.util;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class GreekMapping<T> implements Mapping<T, String> {
  private final GreekCounter counter = new GreekCounter();
  private final Map<T, String> seen = new HashMap<>();

  @Override
  public Map<String, T> mappings() {
    Comparator<String> shorterFirst = (a, b) -> {
      int lenCmp = Integer.compare(a.length(), b.length());
      return lenCmp == 0
        ? a.compareTo(b)
        : lenCmp;
    };
    Map<String, T> toStringMap = new TreeMap<>(shorterFirst);
    seen.forEach((k, v) -> toStringMap.put(v, k));
    return Collections.unmodifiableMap(toStringMap);
  }

  @Override
  public String apply(T t) {
    return seen.computeIfAbsent(t, ignored -> counter.next());
  }

  @Override
  public String toString() {
    Map<String, T> toStringMap = mappings();
    return toStringMap.toString();
  }
}

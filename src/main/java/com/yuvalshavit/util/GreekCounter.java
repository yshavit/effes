package com.yuvalshavit.util;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

public class GreekCounter {
  private static final char ALPHA = 'α';
  private static final char OMEGA = 'ω';

  /**
   * The combination StringBuilder and counter. Since what we have doesn't really neatly fit into 0-based systems (ie, after 9 we want the equivalent of 11,
   * not 10), it's easier to just maintain and manipulate the digits we want straight in the StringBuilder, rather than keeping a counter and translating it
   * to a String each time.
   */
  private final StringBuilder sb = new StringBuilder(4); // 4 digits should be more than enough for most use cases

  public String next() {
    // Go right-to-left, incrementing each char by 1 if you can. For each digit, if you can't increment it, then set it to alpha and go to the next one.
    // If you fall off the edge, prepend another.
    int idx;
    for (idx = sb.length() - 1; idx >= 0; --idx) {
      char digit = sb.charAt(idx);
      if (digit == OMEGA) {
        sb.setCharAt(idx, ALPHA);
      } else {
        // skip word-final sigma 'ς', which is actually the next unicode char after 'ρ'
        char nextChar = (digit == 'ρ')
          ? 'σ'
          : (char) (digit + 1);
        sb.setCharAt(idx, nextChar);
        break;
      }
    }
    if (idx < 0) {
      sb.insert(0, ALPHA);
    }
    return sb.toString();
  }

  public static class Assigner<T> implements Function<T, String> {
    private final GreekCounter counter = new GreekCounter();
    private final Map<T, String> seen = new HashMap<>();

    public Map<String, T> getMappings() {
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
      Map<String, T> toStringMap = getMappings();
      return toStringMap.toString();
    }
  }
}

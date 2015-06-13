package com.yuvalshavit.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.Maps;

public class EfCollections {
  private EfCollections() {}
  
  public static <T> Iterable<Map.Entry<T, T>> zip(Iterable<? extends T> left, Iterable<? extends T> right) {
    return () -> new Iterator<Map.Entry<T, T>>() {
      private final Iterator<? extends T> leftIter = left.iterator();
      private final Iterator<? extends T> rightIter = right.iterator();
      
      @Override
      public boolean hasNext() {
        return leftIter.hasNext() && rightIter.hasNext();
      }

      @Override
      public Map.Entry<T, T> next() {
        T l = leftIter.next();
        T r = rightIter.next();
        return Maps.immutableEntry(l, r);
      }
    };
  }

  public static <I,O> List<? extends O> mapList(Collection<? extends I> c, Function<? super I, ? extends O> f) {
    return c.stream().map(f).collect(Collectors.toList());
  }
}

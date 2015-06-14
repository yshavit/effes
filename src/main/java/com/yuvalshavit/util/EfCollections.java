package com.yuvalshavit.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

public class EfCollections {
  private EfCollections() {}
  
  public static <L, R> Iterable<Map.Entry<? extends L, ? extends R>> zip(Iterable<? extends L> left, Iterable<? extends R> right) {
    return () -> new Iterator<Map.Entry<? extends L,? extends R>>() {
      private final Iterator<? extends L> leftIter = left.iterator();
      private final Iterator<? extends R> rightIter = right.iterator();
      
      @Override
      public boolean hasNext() {
        return leftIter.hasNext() && rightIter.hasNext();
      }

      @Override
      public Map.Entry<? extends L, ? extends R> next() {
        L l = leftIter.next();
        R r = rightIter.next();
        return Maps.immutableEntry(l, r);
      }
    };
  }

  public static <T, U, R> Iterable<? extends R> zip(
    Iterable<? extends T> left,
    Iterable<? extends U> right,
    BiFunction<? super T, ? super U, ? extends R> transform)
  {
    return Iterables.transform(zip(left, right), e -> transform.apply(e.getKey(), e.getValue()));
  }

  public static <I,O> List<? extends O> mapList(Collection<? extends I> c, Function<? super I, ? extends O> f) {
    return c.stream().map(f).collect(Collectors.toList());
  }
}

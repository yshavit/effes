package com.yuvalshavit.util;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.yuvalshavit.effes.compile.node.EfType;

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

  public static <T, U, R> Iterable<R> zipF(
    Iterable<? extends T> left,
    Iterable<? extends U> right,
    BiFunction<? super T, ? super U, ? extends R> transform)
  {
    return Iterables.transform(zip(left, right), e -> transform.apply(e.getKey(), e.getValue()));
  }

  public static <T, U> void zipC(
    Iterable<? extends T> left,
    Iterable<? extends U> right,
    BiConsumer<? super T, ? super U> transform)
  {
    Iterables.size(Iterables.transform( zip(left, right), e -> { transform.accept(e.getKey(), e.getValue()); return null; }));
  }

  public static <I,O> List<? extends O> mapList(Collection<? extends I> c, Function<? super I, ? extends O> f) {
    return c.stream().map(f).collect(Collectors.toList());
  }
  
  public static <T> List<T> concatList(List<T> list, T elem) {
    return concatList(list, Collections.singletonList(elem));
  }
  
  public static <T> List<T> concatList(List<T> list1, List<T> list2) {
    return Lists.newArrayList(Iterables.concat(list1, list2));
  }

  public static <T> Collector<T,?,T> collectOnly() {
    return new OnlyOneCollector<>();
  }

  public static Collector<EfType,?,EfType> efTypeCollector() {
    return new EfTypeCollector();
  }

  private static class OnlyOneCollector<T> implements Collector<T,OnlyOneCollector.Box<T>,T> {
    @Override
    public Supplier<Box<T>> supplier() {
      return Box::new;
    }

    @Override
    public BiConsumer<Box<T>,T> accumulator() {
      return (box, elem) -> {
        Preconditions.checkArgument(box.isEmpty, "can't collect more than one element");
        box.elem = elem;
        box.isEmpty = false;
      };
    }

    @Override
    public BinaryOperator<Box<T>> combiner() {
      return (box1, box2) -> {
        Preconditions.checkState(box1.isEmpty || box2.isEmpty, "can't collect more than one element");
        return box1.isEmpty ? box2 : box1;
      };
    }

    @Override
    public Function<Box<T>,T> finisher() {
      return b -> b.elem;
    }

    @Override
    public Set<Characteristics> characteristics() {
      return EnumSet.noneOf(Characteristics.class);
    }

    static class Box<T> {
      private T elem;
      private boolean isEmpty = true;
    }
  }

  private static class EfTypeCollector implements Collector<EfType,Collection<EfType>,EfType> {

    @Override
    public Supplier<Collection<EfType>> supplier() {
      return ArrayList::new;
    }

    @Override
    public BiConsumer<Collection<EfType>,EfType> accumulator() {
      return Collection::add;
    }

    @Override
    public BinaryOperator<Collection<EfType>> combiner() {
      return (c1, c2) -> { c1.addAll(c2); return c1; };
    }

    @Override
    public Function<Collection<EfType>,EfType> finisher() {
      return EfType::disjunction;
    }

    @Override
    public Set<Characteristics> characteristics() {
      return EnumSet.of(Characteristics.UNORDERED);
    }
  }
}

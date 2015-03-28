package com.yuvalshavit.util;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

public class Equality<T> {
  private final Class<T> clazz;
  private final boolean equalityIncludesSubclasses;
  private final List<Function<? super T, ?>> stateAccessors;

  private Equality(Class<T> clazz, boolean equalityIncludesSubclasses, List<Function<? super T, ?>> stateAccessors) {
    this.clazz = clazz;
    this.equalityIncludesSubclasses = equalityIncludesSubclasses;
    this.stateAccessors = new ArrayList<>(stateAccessors);
  }

  public int hash(T input) {
    if (input == null) {
      return 0;
    }
    if (!(equalityIncludesSubclasses || clazz.equals(input.getClass()))) {
      throw new IllegalArgumentException();
    }
    return Objects.hash(stateAccessors.stream().map(s -> s.apply(input)).toArray());
  }

  public boolean areEqual(Object obj0, Object obj1) {
    if (obj0 == obj1) {
      return true;
    }
    if (obj0 == null || obj1 == null) {
      return false;
    }
    Class<?> obj0Clazz = obj0.getClass();
    Class<?> obj1Clazz = obj1.getClass();
    Predicate<Class<?>> validClass = equalityIncludesSubclasses
      ? clazz::isAssignableFrom
      : clazz::equals;
    if (!(validClass.test(obj0Clazz) && validClass.test(obj1Clazz))) {
      return false;
    }
    T t0 = clazz.cast(obj0);
    T t1 = clazz.cast(obj1);
    return stateAccessors.stream().allMatch(accessor -> Objects.equals(accessor.apply(t0), accessor.apply(t1)));
  }

  @Override
  public String toString() {
    return "EqualityState for " + clazz.getName();
  }

  @Override
  @Deprecated
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  @Deprecated
  public boolean equals(Object other) {
    return super.equals(other);
  }

  public static <T> Builder<T> forClass(Class<T> clazz) {
    return new Builder<>(clazz);
  }

  public static <T> InstanceEqualityBuilder<T> instance(T origin) {
    @SuppressWarnings("unchecked")
    Class<T> originClass = (Class<T>) origin.getClass();
    return new InstanceEqualityBuilder<>(Preconditions.checkNotNull(origin), forClass(originClass));
  }

  public static class InstanceEqualityBuilder<T> {
    private final T origin;
    private final Builder<T> equalityBuilder;

    private InstanceEqualityBuilder(T origin, Builder<T> equalityBuilder) {
      this.origin = origin;
      this.equalityBuilder = equalityBuilder;
    }

    public InstanceEqualityBuilder<T> with(Function<? super T, ?> stateAccessor) {
      equalityBuilder.with(stateAccessor);
      return this;
    }

    public boolean isEqualTo(Object other) {
      return equalityBuilder.exactClassOnly().areEqual(origin, other);
    }

    public int hash() {
      return equalityBuilder.exactClassOnly().hash(origin);
    }

    @Override
    @Deprecated
    public int hashCode() {
      return super.hashCode();
    }

    @Override
    @Deprecated
    public boolean equals(Object other) {
      return super.equals(other);
    }
  }

  public static class Builder<T> {
    private final Class<T> clazz;
    private final List<Function<? super T, ?>> stateAccessors = new ArrayList<>();

    private Builder(Class<T> clazz) {
      this.clazz = clazz;
    }

    public Builder<T> with(Function<? super T, ?> stateAccessor) {
      stateAccessors.add(stateAccessor);
      return this;
    }

    public Equality<T> includingSubclasses() {
      return new Equality<>(clazz, true, stateAccessors);
    }

    public Equality<T> exactClassOnly() {
      return new Equality<>(clazz, false, stateAccessors);
    }

    @Override
    @Deprecated
    public int hashCode() {
      return super.hashCode();
    }

    @Override
    @Deprecated
    public boolean equals(Object other) {
      return super.equals(other);
    }
  }
}

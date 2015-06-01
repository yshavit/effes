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
  private final List<StateElem<? super T>> stateElems;

  private Equality(Class<T> clazz, boolean equalityIncludesSubclasses, List<StateElem<? super T>> stateElems) {
    this.clazz = clazz;
    this.equalityIncludesSubclasses = equalityIncludesSubclasses;
    this.stateElems = new ArrayList<>(stateElems);
  }

  public int hash(T input) {
    if (input == null) {
      return 0;
    }
    if (!(equalityIncludesSubclasses || clazz.equals(input.getClass()))) {
      throw new IllegalArgumentException();
    }
    return Objects.hash(stateElems.stream().map(s -> s.getter().apply(input)).toArray());
  }

  public boolean areEqual(T obj0, Object obj1) {
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
    T t1 = clazz.cast(obj1);
    return stateElems.stream().map(StateElem::getter).allMatch(accessor -> Objects.equals(accessor.apply(obj0), accessor.apply(t1)));
  }
  
  public String toString(T input) {
    StringBuilder sb = new StringBuilder(input.getClass().getSimpleName());
    boolean stateDescribed = false;
    for (StateElem<? super T> stateElem : stateElems) {
      if (stateElem.name() != null) {
        if (stateDescribed) {
          sb.append(", ");
        } else {
          sb.append('[');
          stateDescribed = true;
        }
        sb.append(stateElem.name()).append('=').append(stateElem.getter().apply(input));
      }
    }
    if (stateDescribed) {
      sb.append(']');
    }
    return sb.toString();
  }
  /**
   * Returns the toString <em>of this Equality object</em>. This is probably not what you want; use 
   * {@link #toString(Object)} if you want to get the toString of a given <code>T</code>.
   *
   * This method simply says that this is an Equality for a given class.
   */
  @Deprecated
  @Override
  public String toString() {
    return getClass().getSimpleName() + " for " + clazz.getName();
  }

  /**
   * Returns the hash code <em>of theis Equality object</em>. This is probably not what you want; use 
   * {@link #hash(Object)} if you want to get the hash code of a given <code>T</code>.
   * 
   * This method simply uses <code>Object.hashCode</code>.
   */
  @Override
  @Deprecated
  public int hashCode() {
    return super.hashCode();
  }

  /**
   * Returns the whether the given object equals <em>this Equality object</em>. This is probably not what you want; use 
   * {@link #areEqual(Object, Object)} if you want to know whether two <code>T</code>s are equal.
   *
   * This method simply uses <code>Object.equals</code>.
   */
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
    private final List<StateElem<? super T>> stateAccessors = new ArrayList<>();

    private Builder(Class<T> clazz) {
      this.clazz = clazz;
    }
    
    public Builder<T> with(String name, Function<? super T, ?> stateAccessor) {
      stateAccessors.add(new StateElem<>(name, stateAccessor));
      return this;
    }

    public Builder<T> with(Function<? super T, ?> stateAccessor) {
      return with(null, stateAccessor);
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
  
  private static class StateElem<T> {
    private final String name;
    private final Function<? super T, ?> getter;

    public StateElem(String name, Function<? super T, ?> getter) {
      this.name = name;
      this.getter = Preconditions.checkNotNull(getter);
    }

    public String name() {
      return name;
    }

    public Function<? super T, ?> getter() {
      return getter;
    }
  }
}

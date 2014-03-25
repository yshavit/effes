package com.yuvalshavit.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public class Dispatcher<D,I,O> implements BiFunction<D, I, O> {
  @VisibleForTesting
  final Map<Class<? extends I>, BiFunction<? super D, I, ? extends O>> dispatches;
  @VisibleForTesting
  final Class<?> baseClass;

  private Dispatcher(Class<I> baseClass,
                     Map<Class<? extends I>, BiFunction<? super D, I, ? extends O>> builder)
  {
    this.baseClass = baseClass;
    if (builder.containsKey(baseClass)) {
      throw new IllegalArgumentException("dispatcher must not explicitly handle the base class (" + baseClass + ")");
    }
    this.dispatches = ImmutableMap.<Class<? extends I>, BiFunction<? super D, I, ? extends O>>builder()
      .putAll(builder)
      .put(baseClass, this::apply)
      .build();
  }

  @Override
  public O apply(D dispatch, I input) {
    @SuppressWarnings("SuspiciousMethodCalls")
    BiFunction<? super D, I, ? extends O> dispatchFunc = dispatches.get(input.getClass());
    if (dispatchFunc == null) {
      throw new IllegalArgumentException("no dispatch function for " + input.getClass());
    }
    return dispatchFunc.apply(dispatch, input);
  }

  public static <D, I, O> Builder<D, I, O> builder(Class<D> dispatchClass, Class<I> baseClass, Class<O> resultClass) {
    return new Builder<>(baseClass);
  }

  public static class Builder<D, I, O> {
    private final Class<I> baseClass;

    private Builder(Class<I> baseClass) {
      this.baseClass = Preconditions.checkNotNull(baseClass);
    }

    private final Map<Class<? extends I>, BiFunction<? super D, I, ? extends O>> dispatches = new HashMap<>();

    public <C extends I> Builder<D, I, O> put(Class<C> dispatchOn, BiFunction<? super D, C, ? extends O> func) {
      // "func" will only be invoked on an input i when (i instanceof C).
      // In fact, it'll only happen when i.getClass() == C. The compiler has no way of knowing that, but it's ensured
      // by code above. We could do "f = (d, i) -> func.apply(d, dispatchOn.cast(i))" without any casting issues,
      // which validates that this is okay as long as dispatchOn.cast(i) succeeds -- which we know it will, since
      // i.getClass() == dispatchOn.
      @SuppressWarnings("unchecked")
      BiFunction<? super D, I, ? extends O> f = (BiFunction<? super D, I, ? extends O>) func;
      dispatches.put(dispatchOn, f);
      return this;
    }

    public Dispatcher<D, I, O> build() {
      return new Dispatcher<>(baseClass, dispatches);
    }
  }
}

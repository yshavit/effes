package com.yuvalshavit.effes.compile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class Scopes<T,D> {
  private final Function<? super T, String> namer;
  private final BiConsumer<? super String, ? super D> onDuplicate;

  public Scopes(Function<? super T, String> namer, BiConsumer<? super String, ? super D> onDuplicate) {
    this.namer = namer;
    this.onDuplicate = onDuplicate;
  }

  private final Deque<Map<String, T>> scopes = new ArrayDeque<>();

  public void pushScope() {
    scopes.push(new HashMap<>());
  }

  public void popScope() {
    try {
      scopes.pop();
    } catch (NoSuchElementException e) {
      throw new IllegalStateException("no more scopes to pop");
    }
  }

  public int depth() {
    return scopes.size();
  }

  @Nullable
  public T get(String name) {
    if (scopes.isEmpty()) {
      throw new IllegalStateException("no active scope");
    }
    for(Map<String, T> scope : scopes) {
      T var = scope.get(name);
      if (var != null) {
        return var;
      }
    }
    return null;
  }

  public void add(@Nonnull T var, D duplicateId) {
    Map<String, T> scope = scopes.peek();
    if (scope == null) {
      throw new IllegalStateException("no active scope");
    }
    String name = namer.apply(var);
    if (scope.containsKey(name)) {
      onDuplicate.accept(name, duplicateId);
    } else {
      scope.put(name, var);
    }
  }
}

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

/**
 * Scoping handler
 * @param <T> the thing that's being scoped; vars, methods, etc
 * @param <D> extra information that's used in the case of a duplicate; e.g. the token that a variable name came from
 */
public final class Scopes<T,D> {
  private final Function<? super T, String> namer;
  private final BiConsumer<? super String, ? super D> onDuplicate;
  private final ScopeCloser closer = new ScopeCloser(this);
  private final Deque<Scope<T>> scopes = new ArrayDeque<>();
  private int elemsCountOffset = 0;

  /**
   * @param namer a function that names a T; each name must be unique within a scope
   * @param onDuplicate error handler for duplicates; takes the duplicated T and the extra information D
   */
  public Scopes(Function<? super T, String> namer, BiConsumer<? super String, ? super D> onDuplicate) {
    this.namer = namer;
    this.onDuplicate = onDuplicate;
  }

  public ScopeCloser pushScope() {
    scopes.push(new Scope<>());
    return closer;
  }

  public void popScope() {
    try {
      scopes.pop();
    } catch (NoSuchElementException e) {
      throw new IllegalStateException("no more scopes to pop");
    }
  }

  public int countElems() {
    return scopes.stream().mapToInt(Scope::size).sum() - elemsCountOffset;
  }

  public void setElemsCountOffset(int offset) {
    elemsCountOffset = offset;
  }

  public int depth() {
    return scopes.size();
  }

  @Nullable
  public T get(String name) {
    if (scopes.isEmpty()) {
      throw new IllegalStateException("no active scope");
    }
    for(Scope<T> scope : scopes) {
      T var = scope.byName.get(name);
      if (var != null) {
        return var;
      }
    }
    return null;
  }

  public void add(@Nonnull T var, D duplicateId) {
    Scope<T> scope = scopes.peek();
    if (scope == null) {
      throw new IllegalStateException("no active scope");
    }
    String name = namer.apply(var);
    if (get(name) != null) {
      onDuplicate.accept(name, duplicateId);
    } else {
      scope.byName.put(name, var);
    }
  }

  public void replace(@Nonnull T var) {
    Scope<T> scope = scopes.peek();
    if (scope == null) {
      throw new IllegalStateException("no active scope");
    }
    String name = namer.apply(var);
    if (get(name) == null) {
      throw new IllegalArgumentException("no such variable to replace: " + name);
    } else if (scope.byName.containsKey(name)) {
      throw new IllegalArgumentException("can't replace within same scope: " + name);
    } else {
      scope.byName.put(name, var);
      ++scope.elemsCountOffset;
    }
  }

  public String uniqueName() {
    int n = 0;
    String name;
    do {
      name = "$" + n;
    } while (get(name) != null);
    return name;
  }

  public static class ScopeCloser implements AutoCloseable {
    private final Scopes<?,?> parent;

    private ScopeCloser(Scopes<?,?> parent) {
      this.parent = parent;
    }

    @Override
    public void close() {
      parent.popScope();
    }
  }

  private static class Scope<T> {
    private final Map<String, T> byName = new HashMap<>();
    private int elemsCountOffset = 0;

    int size() {
      return byName.size() - elemsCountOffset;
    }
  }
}

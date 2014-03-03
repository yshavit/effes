package com.yuvalshavit.effes.interpreter;

import com.google.common.collect.Maps;

import java.util.Map;

public class Scopes<T> {
  private Scope<T> current = new Scope<>(null);

  public void push() {
    current = new Scope<>(current);
  }

  public void pop() {
    Scope<T> prev = current.parent;
    if (prev == null) {
      throw new IllegalStateException("can't pop past last scope");
    }
    current = prev;
  }

  public T lookUp(String name) {
    for (Scope<T> scope = current; scope != null; scope = scope.parent) {
      T found = scope.variables.get(name);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private static class Scope<T> {
    private final Scope<T> parent;
    private final Map<String, T> variables;

    private Scope(Scope<T> parent) {
      this.parent = parent;
      this.variables = Maps.newHashMap();
    }
  }
}

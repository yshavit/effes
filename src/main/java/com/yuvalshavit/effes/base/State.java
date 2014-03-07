package com.yuvalshavit.effes.base;

import com.google.common.collect.Queues;

import java.util.Deque;

public class State {
  private final Deque<EfVariable> declaringContext = Queues.newArrayDeque();
  private final Deque<EfVariable> stack = Queues.newArrayDeque();
  private final Scopes<EfVariable> scopes = new Scopes<>();
  private final TypeRegistery registery;

  public State(TypeRegistery registery) {
    this.registery = registery;
  }

  public EfVariable getDeclaringContext() {
    return declaringContext.peek();
  }

  public Deque<EfVariable> getStack() {
    return stack;
  }

  public Scopes<EfVariable> getScopes() {
    return scopes;
  }

  public TypeRegistery typeRegistry() {
    return registery;
  }
}

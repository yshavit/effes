package com.yuvalshavit.effes.interpreter;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class StateStack {
  private final Deque<Object> states = new ArrayDeque<>();

  public void push(Object state) {
    states.push(state);
  }

  public Object pop() {
    return states.pop();
  }

  @VisibleForTesting
  public List<Object> getAllStates() {
    return ImmutableList.copyOf(states);
  }
}

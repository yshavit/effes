package com.yuvalshavit.effes.base;

import java.util.Arrays;
import java.util.List;

public class EfVariable {
  private final Type type;
  private final List<Object> state;

  public EfVariable(Type type, List<Object> state) {
    this.type = type;
    this.state = state;
  }

  public EfVariable(Type type, Object... state) {
    this(type, Arrays.asList(state));
  }

  public Type getType() {
    return type;
  }

  public List<Object> getState() {
    return state;
  }
}

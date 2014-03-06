package com.yuvalshavit.effes.interpreter;

import java.util.Arrays;
import java.util.List;

public class EfVariable {
  private final EfType type;
  private final List<Object> state;

  public EfVariable(EfType type, List<Object> state) {
    this.type = type;
    this.state = state;
  }

  public EfVariable(EfType type, Object... state) {
    this(type, Arrays.asList(state));
  }

  public EfType getType() {
    return type;
  }

  public List<Object> getState() {
    return state;
  }
}

package com.yuvalshavit.effes.interpreter;

public class EfVariable {
  private final EfType type;

  public EfVariable(EfType type) {
    this.type = type;
  }

  public EfType getType() {
    return type;
  }
}

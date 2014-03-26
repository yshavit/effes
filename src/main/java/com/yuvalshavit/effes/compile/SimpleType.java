package com.yuvalshavit.effes.compile;

public final class SimpleType {
  private final String name;

  public SimpleType(String name) {
    assert name != null;
    this.name = name;
  }

  @Override
  public String toString() {
    return name;
  }

  public boolean contains(SimpleType other) {
    return equals(other);
  }
}

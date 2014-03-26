package com.yuvalshavit.effes.compile;

public final class SimpleType {
  private final String name;

  public SimpleType(String name) {
    assert name != null;
    this.name = name;
  }

  @Override
  public boolean equals(Object obj) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Override
  public String toString() {
    return name;
  }
}

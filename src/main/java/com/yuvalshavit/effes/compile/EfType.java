package com.yuvalshavit.effes.compile;

public abstract class EfType {

  private EfType() {}

  public abstract boolean contains(EfType other);

  public static final class SimpleType extends EfType {
    private final String name;

    public SimpleType(String name) {
      assert name != null;
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }

    @Override
    public boolean contains(EfType other) {
      return equals(other);
    }
  }
}

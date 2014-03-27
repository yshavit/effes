package com.yuvalshavit.effes.compile;

public abstract class CaseMatcher {
  private CaseMatcher() {}

  public static class SimpleCtorMatch extends CaseMatcher {
    private final EfType.SimpleType type;

    public SimpleCtorMatch(EfType.SimpleType type) {
      this.type = type;
    }

    public EfType.SimpleType getType() {
      return type;
    }
  }
}
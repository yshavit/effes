package com.yuvalshavit.effes.compile;

public abstract class CaseMatcher {
  private CaseMatcher() {}

  public abstract EfType getType();

  public static class SimpleCtorMatch extends CaseMatcher {
    private final EfType.SimpleType type;

    public SimpleCtorMatch(EfType.SimpleType type) {
      this.type = type;
    }

    public EfType.SimpleType getType() {
      return type;
    }

    @Override
    public String toString() {
      return type.toString();
    }
  }

  public static class ErrorMatch extends CaseMatcher {
    public static final CaseMatcher instance = new ErrorMatch();

    private ErrorMatch() {}

    @Override
    public EfType getType() {
      return EfType.UNKNOWN;
    }
  }
}

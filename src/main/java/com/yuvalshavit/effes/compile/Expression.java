package com.yuvalshavit.effes.compile;

import javax.annotation.Nonnull;

public abstract class Expression extends StatefulObject {
  private Expression() {}

  public static class CtorInvoke extends Expression {
    private final SimpleType type;

    public CtorInvoke(@Nonnull SimpleType type) {
      this.type = type;
    }

    @Override
    public String toString() {
      return String.format("%s()", type);
    }

    @Override
    protected Object[] state() {
      return new Object[] { type };
    }
  }
}

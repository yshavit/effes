package com.yuvalshavit.effes.compile;

import javax.annotation.Nonnull;
import java.util.Arrays;

public abstract class Expression {
  private Expression() {}

  protected abstract Object[] state();

  @Override
  public final boolean equals(Object o) {
    return this == o
      || ((o != null)
          && (getClass() == o.getClass())
          && Arrays.deepEquals(state(), ((Expression) o).state()));
  }

  @Override
  public final int hashCode() {
    return Arrays.deepHashCode(state());
  }

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

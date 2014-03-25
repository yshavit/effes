package com.yuvalshavit.effes.compile;

import java.util.Arrays;

abstract class StatefulObject {
  protected abstract Object[] state();

  @Override
  public final boolean equals(Object o) {
    return this == o
      || ((o != null)
          && (getClass() == o.getClass())
          && Arrays.equals(state(), ((StatefulObject) o).state()));
  }

  @Override
  public final int hashCode() {
    return Arrays.hashCode(state());
  }

  @Override
  public abstract String toString();
}

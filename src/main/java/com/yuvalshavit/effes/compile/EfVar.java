package com.yuvalshavit.effes.compile;

import com.google.common.base.Preconditions;

public final class EfVar {
  private static final int IS_UNKNOWN = Integer.MIN_VALUE;
  private final EfType type;
  private final String name;
  private final int argPosition;

  public static EfVar arg(String name, int argPosition, EfType type) {
    Preconditions.checkArgument(argPosition >= 0, "invalid arg position: %d", argPosition);
    return new EfVar(name, argPosition, type);
  }

  public static EfVar unknown(String name) {
    return new EfVar(name, IS_UNKNOWN, EfType.UNKNOWN);
  }

  private EfVar(String name, int argPosition, EfType type) {
    this.type = type;
    this.name = name;
    this.argPosition = argPosition;
  }

  public int getArgPosition() {
    if (argPosition < 0) {
      throw new IllegalStateException("invalid arg variable (" + argPosition + ')');
    }
    return argPosition;
  }

  public String getName() {
    return name;
  }

  public EfType getType() {
    return type;
  }

  @Override
  public String toString() {
    return name;
  }
}

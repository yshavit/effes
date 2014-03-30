package com.yuvalshavit.effes.compile;

import com.google.common.base.Preconditions;

public final class EfVar {
  private static final int IS_UNKNOWN = -1;
  private final EfType type;
  private final String name;
  private final int argPosition;
  private final boolean isArg;

  public static EfVar arg(String name, int argPosition, EfType type) {
    Preconditions.checkArgument(argPosition >= 0, "invalid arg position: %s", argPosition);
    return new EfVar(name, argPosition, type, true);
  }

  public static EfVar var(String name, int varPosition, EfType type) {
    Preconditions.checkArgument(varPosition >= 0, "invalid arg position: %s", varPosition);
    return new EfVar(name, varPosition, type, false);
  }

  public static EfVar unknown(String name) {
    return new EfVar(name, IS_UNKNOWN, EfType.UNKNOWN, false);
  }

  private EfVar(String name, int argPosition, EfType type, boolean isArg) {
    this.type = type;
    this.name = name;
    this.argPosition = argPosition;
    this.isArg = isArg;
  }

  public int getArgPosition() {
    return argPosition;
  }

  public String getName() {
    return name;
  }

  public EfType getType() {
    return type;
  }

  public boolean isArg() {
    return isArg;
  }

  @Override
  public String toString() {
    return String.format("%s (pos=%d)", name, argPosition);
  }
}

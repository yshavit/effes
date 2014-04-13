package com.yuvalshavit.effes.compile;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

public final class MethodId implements Comparable<MethodId> {
  @Nullable
  private final EfType.SimpleType definedOn;
  @Nonnull
  private final String name;

  public static MethodId topLevel(@Nonnull String name) {
    return new MethodId(null, name);
  }

  public static MethodId onType(@Nonnull EfType.SimpleType definedOn, @Nonnull String name) {
    return new MethodId(definedOn, name);
  }

  public static MethodId of(@Nullable EfType.SimpleType definedOn, @Nonnull String name) {
    return new MethodId(definedOn, name);
  }

  private MethodId(@Nullable EfType.SimpleType definedOn, @Nonnull String name) {
    this.definedOn = definedOn;
    this.name = name;
  }

  @Nullable
  public EfType.SimpleType getDefinedOn() {
    return definedOn;
  }

  @Nonnull
  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return definedOn != null
      ? String.format("%s::%s", definedOn, name)
      : name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MethodId other = (MethodId) o;
    return Objects.equals(definedOn, other.definedOn) && name.equals(other.name);
  }

  @Override
  public int hashCode() {
    int result = definedOn != null ? definedOn.hashCode() : 0;
    result = 31 * result + name.hashCode();
    return result;
  }

  @Override
  public int compareTo(@Nonnull MethodId other) {
    boolean thisIsTopLevel = definedOn == null;
    boolean otherIsTopLevel = other.definedOn == null;
    if (thisIsTopLevel != otherIsTopLevel) {
      return thisIsTopLevel ? -1 : 1;
    }
    return name.compareTo(other.name);
  }
}

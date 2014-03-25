package com.yuvalshavit.effes.compile;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import java.util.List;

public final class EfMethod {
  private final List<SimpleType> argTypes;
  private final SimpleType resultType;

  public EfMethod(List<SimpleType> argTypes, SimpleType resultType) {
    this.argTypes = ImmutableList.copyOf(argTypes);
    this.resultType = resultType;
  }

  @Override
  public String toString() {
    return String.format("(%s) -> %s", Joiner.on(", ").join(argTypes), resultType);
  }
}

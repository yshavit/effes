package com.yuvalshavit.effes.interpreter;

import com.google.common.collect.ImmutableList;

public class TupleVar extends EfVariable {
  private final ImmutableList<EfVariable> values;

  public TupleVar(EfType type, ImmutableList<EfVariable> values) {
    super(type);
    this.values = values;
  }
}

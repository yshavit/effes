package com.yuvalshavit.effes.interpreter;

import com.google.common.collect.ImmutableList;

public class TupleType extends EfType {
  private final ImmutableList<EfType> types;

  public TupleType(ImmutableList<EfType> types) {
    super(null);
    this.types = types;
    throw new UnsupportedOperationException(); // TODO
  }


}

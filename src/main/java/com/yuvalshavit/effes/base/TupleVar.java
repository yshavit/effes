package com.yuvalshavit.effes.base;

import com.google.common.collect.ImmutableList;

public class TupleVar extends EfVariable {

  public TupleVar(EfType type, ImmutableList<EfVariable> values) {
    super(type, values);
  }
}

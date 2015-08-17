package com.yuvalshavit.effes.compile.pmatch;

import com.yuvalshavit.effes.compile.node.EfType;

public class ForcedPossibility {
  final PPossibility possibility;
  final EfType efType;

  public ForcedPossibility(PPossibility possibility, EfType efType) {
    this.possibility = possibility;
    this.efType = efType;
  }

  public PPossibility possibility() {
    return possibility;
  }

  public EfType efType() {
    return efType;
  }
}

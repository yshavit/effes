package com.yuvalshavit.effes.compile.pmatch;

import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.util.Lazy;

public class LazyPossibility {
  final Lazy<PPossibility> possibility;
  final EfType efType;

  public LazyPossibility(Lazy<PPossibility> possibility, EfType efType) {
    this.possibility = possibility;
    this.efType = efType;
  }

  public Lazy<PPossibility> possibility() {
    return possibility;
  }

  public EfType efType() {
    return efType;
  }

  @Override
  public String toString() {
    return efType.toString();
  }
}

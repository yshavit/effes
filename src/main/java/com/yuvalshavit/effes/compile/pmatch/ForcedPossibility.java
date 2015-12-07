package com.yuvalshavit.effes.compile.pmatch;

import java.util.Collections;
import java.util.Map;

import com.yuvalshavit.effes.compile.node.EfType;

public class ForcedPossibility {
  final PPossibility possibility;
  final EfType efType;
  private final Map<String, EfType> bindings;

  public ForcedPossibility(PPossibility possibility, EfType efType, Map<String, EfType> bindings) {
    this.possibility = possibility;
    this.efType = efType;
    this.bindings = bindings;
  }

  public ForcedPossibility(EfType type) {
    this(PPossibility.from(type), type, Collections.emptyMap());
  }

  public PPossibility possibility() {
    return possibility;
  }

  public EfType efType() {
    return efType;
  }

  public Map<String, EfType> bindings() {
    return bindings;
  }
}

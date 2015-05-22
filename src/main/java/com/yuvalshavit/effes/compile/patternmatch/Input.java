package com.yuvalshavit.effes.compile.patternmatch;

import com.yuvalshavit.effes.compile.node.EfType;

import java.util.Set;

public class Input {
  private boolean containsGeneric;
  private final Set<EfType.SimpleType> inputTypes;

  public Input(Set<EfType.SimpleType> inputTypes) {
    this.inputTypes = inputTypes;
  }
}

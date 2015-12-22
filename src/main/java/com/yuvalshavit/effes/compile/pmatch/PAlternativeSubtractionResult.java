package com.yuvalshavit.effes.compile.pmatch;

import java.util.Collections;
import java.util.Map;

import com.yuvalshavit.effes.compile.node.EfType;

public class PAlternativeSubtractionResult {
  final PPossibility possibility;
  final EfType remainingType;
  private final Map<String, EfType> bindings;
  private final EfType matchedType;

  public PAlternativeSubtractionResult(PPossibility remainingPossibility, EfType remainingType, Map<String, EfType> bindings, EfType matchedType) {
    this.possibility = remainingPossibility;
    this.remainingType = remainingType;
    this.bindings = bindings;
    this.matchedType = matchedType;
  }

  public PAlternativeSubtractionResult(EfType type) {
    this(PPossibility.from(type), type, Collections.emptyMap(), null);
  }

  public PPossibility remainingPossibility() {
    return possibility;
  }

  public EfType remainingResultType() {
    return remainingType;
  }

  public EfType matchedType() {
    return matchedType;
  }

  public Map<String, EfType> bindings() {
    return bindings;
  }
}

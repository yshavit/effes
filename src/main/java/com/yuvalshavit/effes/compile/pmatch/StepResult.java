package com.yuvalshavit.effes.compile.pmatch;

import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.Collection;

import javax.annotation.Nonnull;

import com.yuvalshavit.util.Lazy;

class StepResult {
  private final Collection<Lazy<PPossibility>> matched = new ArrayList<>();
  private final Collection<Lazy<PPossibility>> unmatched = new ArrayList<>();
  
  public boolean noneMatched() {
    return matched.isEmpty();
  }
  
  public boolean anyMatched() {
    return ! noneMatched();
  }
  
  @Nonnull
  public Collection<Lazy<PPossibility>> getMatched() {
    checkState(anyMatched(), "no matched");
    return matched;
  }
  
  @Nonnull
  public Collection<Lazy<PPossibility>> getUnmatched() {
    return unmatched;
  }

  public void addUnmatched(Lazy<PPossibility> unmatched) {
    this.unmatched.add(unmatched);
  }
  
  public void addMatched(Lazy<PPossibility> matched) {
    this.matched.add(matched);
  }
  
  public void addMatched(Collection<Lazy<PPossibility>> matched) {
    this.matched.addAll(matched);
  }

  public void addUnmatched(Collection<Lazy<PPossibility>> unmatched) {
    this.unmatched.addAll(unmatched);
  }

  @Override
  public String toString() {
    return "matched=" + (anyMatched() ? matched : "∅") + ", unmatched=" + unmatched;
  }
}

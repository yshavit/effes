package com.yuvalshavit.effes.compile.pmatch;

import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import javax.annotation.Nonnull;

class StepResult {
  private final Collection<LazyPossibility> matched = new ArrayList<>();
  private final Collection<LazyPossibility> unmatched = new ArrayList<>();
  
  public boolean noneMatched() {
    return matched.isEmpty();
  }
  
  public boolean anyMatched() {
    return ! noneMatched();
  }
  
  @Nonnull
  public Collection<LazyPossibility> getMatched() {
    checkState(anyMatched(), "no matched");
    return matched;
  }
  
  @Nonnull
  public Collection<LazyPossibility> getUnmatched() {
    return unmatched;
  }

  public void addUnmatched(LazyPossibility unmatched) {
    this.unmatched.add(unmatched);
  }
  
  public void addMatched(LazyPossibility matched) {
    this.matched.add(matched);
  }
  
  public void addMatched(Collection<? extends LazyPossibility> matched) {
    this.matched.addAll(matched);
  }

  public void addUnmatched(Collection<LazyPossibility> unmatched) {
    this.unmatched.addAll(unmatched);
  }

  @Override
  public String toString() {
    return "matched=" + (anyMatched() ? matched : "∅") + ", unmatched=" + unmatched;
  }
}

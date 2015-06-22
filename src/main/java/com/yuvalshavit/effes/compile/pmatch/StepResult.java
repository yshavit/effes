package com.yuvalshavit.effes.compile.pmatch;

import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.Collection;

import javax.annotation.Nonnull;

class StepResult {
  private PPossibility matched = null;
  private final Collection<PPossibility> unmatched = new ArrayList<>();
  
  public boolean noneMatched() {
    return matched == null;
  }
  
  public boolean anyMatched() {
    return ! noneMatched();
  }
  
  @Nonnull
  public PPossibility getOnlyMatched() {
    checkState(anyMatched(), "no matched");
    return matched;
  }
  
  @Nonnull
  public Collection<PPossibility> getUnmatched() {
    return unmatched;
  }

  public void addUnmatched(PPossibility unmatched) {
    this.unmatched.add(unmatched);
  }
  
  public void setMatched(PPossibility matched) {
    checkState(noneMatched(), "already matched: " + this.matched);
    this.matched = matched;
  }

  public void addUnmatched(Collection<? extends PPossibility> unmatched) {
    this.unmatched.addAll(unmatched);
  }

  @Override
  public String toString() {
    return "matched=" + (anyMatched() ? matched : "∅") + ", unmatched=" + unmatched;
  }
}

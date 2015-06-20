package com.yuvalshavit.effes.compile.pmatch;

import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.Collection;

import javax.annotation.Nonnull;

import com.yuvalshavit.util.Lazy;

class StepResult {
  private PPossibility matched = null;
  private final Collection<Lazy<? extends PPossibility>> unmatched = new ArrayList<>();
  
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
  public Collection<Lazy<? extends PPossibility>> getUnmatched() {
    return unmatched;
  }

  public void addUnmatched(Lazy<? extends PPossibility> unmatched) {
    this.unmatched.add(unmatched);
  }
  
  public void addUnmatched(PPossibility.Simple unmatched) {
    this.unmatched.add(Lazy.forced(unmatched));
  }
  
  public void setMatched(PPossibility matched) {
    checkState(noneMatched(), "already matched: " + this.matched);
    this.matched = matched;
  }

  public void addUnmatched(Collection<? extends Lazy<? extends PPossibility>> unmatched) {
    this.unmatched.addAll(unmatched);
  }

  @Override
  public String toString() {
    return "matched=" + (anyMatched() ? matched : "∅") + ", unmatched=" + unmatched;
  }
}

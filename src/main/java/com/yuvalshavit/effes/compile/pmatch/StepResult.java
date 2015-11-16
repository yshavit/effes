package com.yuvalshavit.effes.compile.pmatch;

import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.Nonnull;

import com.yuvalshavit.effes.compile.node.EfType;

class StepResult {
  private final Collection<LazyPossibility> matched = new ArrayList<>();
  private final Collection<LazyPossibility> unmatched = new ArrayList<>();
  private final Map<String, EfType> bindings = new TreeMap<>();
  
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

  public void addBinding(String name, EfType var) {
    bindings.merge(name, var, EfType::disjunction);
  }

  public void addBindings(Map<String, EfType> bindings) {
    bindings.forEach(this::addBinding);
  }

  @Override
  public String toString() {
    return String.format("matched=%s, unmatched=%s, bound=%s", anyMatched() ? matched : "∅", unmatched, bindings.isEmpty() ? "∅" : bindings);
  }

  public Map<String, EfType> bindings() {
    return bindings;
  }
}

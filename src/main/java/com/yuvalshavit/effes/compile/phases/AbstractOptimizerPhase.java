package com.yuvalshavit.effes.compile.phases;

import com.google.common.collect.ImmutableList;

public abstract class AbstractOptimizerPhase implements OptimizerPhase {
  private final ImmutableList<OptimizerPhase> dependencies;

  protected AbstractOptimizerPhase(OptimizerPhase... dependencies) {
    this.dependencies = ImmutableList.copyOf(dependencies);
  }

  @Override
  public final ImmutableList<OptimizerPhase> dependencies() {
    return dependencies;
  }
}

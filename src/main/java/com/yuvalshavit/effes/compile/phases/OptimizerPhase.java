package com.yuvalshavit.effes.compile.phases;

import com.google.common.collect.ImmutableList;
import com.yuvalshavit.effes.ir.EfNode;

public interface OptimizerPhase {
  void apply(EfNode node);
  ImmutableList<OptimizerPhase> dependencies();

}

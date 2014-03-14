package com.yuvalshavit.effes.compile.phases;

import com.yuvalshavit.effes.ir.EfNode;
import com.yuvalshavit.effes.ir.EfVisitor;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class Optimizer {
  private Map<OptimizerPhase, Visitor> visitors;

  public Optimizer(List<OptimizerPhase> phases) {
    Map<OptimizerPhase, Visitor> m = phases.stream().collect(Collectors.toMap((OptimizerPhase t) -> t, Visitor::new));
    visitors = new IdentityHashMap<>(m);
  }

  void apply(EfNode node) {
    for (Visitor v : visitors.values()) {
      applyOne(node, v);
    }
  }

  private void applyOne(EfNode node, Visitor v) {
    for (OptimizerPhase childPhase : v.phase.dependencies()) {
      Visitor childVisitor = visitors.remove(childPhase);
      if (childVisitor != null) {
        applyOne(node, childVisitor);
      }
    }
    v.visit(node);
  }

  private static class Visitor implements EfVisitor {
    private final OptimizerPhase phase;

    private Visitor(OptimizerPhase phase) {
      this.phase = phase;
    }

    @Override
    public void onEnter(EfNode node) {
      // nothing
    }

    @Override
    public void onExit(EfNode node) {
      phase.apply(node);
    }
  }
}

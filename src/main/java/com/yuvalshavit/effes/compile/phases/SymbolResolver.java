package com.yuvalshavit.effes.compile.phases;

import com.yuvalshavit.effes.ir.EfNode;

public final class SymbolResolver extends AbstractOptimizerPhase {

  public SymbolResolver(TypeFinder typeFinder) {
    super(typeFinder);
  }

  @Override
  public void apply(EfNode node) {
    throw new UnsupportedOperationException(); // TODO
  }
}

package com.yuvalshavit.effes.compile.phases;

import com.yuvalshavit.effes.base.BuiltIns;
import com.yuvalshavit.effes.base.TypeRegistry;
import com.yuvalshavit.effes.ir.EfNode;

public final class TypeFinder extends AbstractOptimizerPhase {
  private final TypeRegistry registry = BuiltIns.createRegistry();

  @Override
  public void apply(EfNode node) {

  }
}

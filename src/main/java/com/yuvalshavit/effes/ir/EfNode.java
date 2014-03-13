package com.yuvalshavit.effes.ir;

import com.google.common.collect.ImmutableList;

public interface EfNode {
  ImmutableList<? extends EfNode> children();
}

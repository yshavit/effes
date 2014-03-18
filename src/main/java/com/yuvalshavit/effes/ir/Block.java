package com.yuvalshavit.effes.ir;

import com.google.common.collect.ImmutableList;

import java.util.List;

public final class Block implements EfNode {
  private final ImmutableList<Statement> statements;

  public Block(ImmutableList<Statement> statements) {
    this.statements = statements;
  }

  public Block(List<Statement> statements) {
    this(ImmutableList.copyOf(statements));
  }

  @Override
  public ImmutableList<? extends EfNode> children() {
    return statements;
  }
}

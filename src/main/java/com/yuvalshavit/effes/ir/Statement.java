package com.yuvalshavit.effes.ir;

import com.google.common.collect.ImmutableList;

public abstract class Statement implements EfNode {

  private Statement() {}

  public static class IfElseStatement extends Statement {
    private final Expression cond;
    private final Block trueBlock;
    private final Block falseBlock;

    public IfElseStatement(Expression cond, Block trueBlock, Block falseBlock) {

      this.cond = cond;
      this.trueBlock = trueBlock;
      this.falseBlock = falseBlock;
    }

    @Override
    public ImmutableList<? extends EfNode> children() {
      return ImmutableList.of(cond, trueBlock, falseBlock);
    }
  }

  public static class AssignStat extends Statement {
    private final String varName;
    private final Expression value;

    public AssignStat(String varName, Expression value) {
      this.varName = varName;
      this.value = value;
    }

    @Override
    public ImmutableList<? extends EfNode> children() {
      return ImmutableList.of(value);
    }
  }

  public static class ReturnStat extends Statement {
    private final Expression value;

    public ReturnStat(Expression value) {
      this.value = value;
    }

    @Override
    public ImmutableList<? extends EfNode> children() {
      return ImmutableList.of(value);
    }
  }
}

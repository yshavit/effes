package com.yuvalshavit.effes.compile;

import com.google.common.collect.ImmutableList;
import com.yuvalshavit.util.Dispatcher;

import java.util.List;

public final class Block {

  private final List<Statement> statements;

  public Block(List<Statement> statements) {
    this.statements = ImmutableList.copyOf(statements);
  }

  public List<Statement> statements() {
    return statements;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Block block = (Block) o;
    return statements.equals(block.statements);
  }

  @Override
  public int hashCode() {
    return statements.hashCode();
  }

  public void validate(boolean requireReturn) {
    ValidationDispatch validator = new ValidationDispatch();
    BranchReturn lastBranch = BranchReturn.NEVER;
    for (Statement s : statements) {
      if (lastBranch == BranchReturn.ALWAYS) {
        throw new BlockValidationException("unreachable statement: " + s);
      }
      lastBranch = ValidationDispatch.dispatcher.apply(validator, s);
    }
    if (requireReturn && lastBranch != BranchReturn.ALWAYS) {
      throw new BlockValidationException("block may not return");
    }
  }

  private enum BranchReturn {
    ALWAYS,
    NEVER
  }

  static class ValidationDispatch {
    static final Dispatcher<ValidationDispatch, Statement, BranchReturn> dispatcher =
      Dispatcher.<ValidationDispatch, Statement, BranchReturn>builder(Statement.class)
        .put(Statement.ReturnStatement.class, ValidationDispatch::returnStat)
        .build();

    public BranchReturn returnStat(Statement.ReturnStatement ctx) {
      return BranchReturn.ALWAYS;
    }
  }
}

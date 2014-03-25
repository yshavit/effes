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

  public void validate(SimpleType requiredReturnType) {
    ValidationDispatch validator = new ValidationDispatch();
    SimpleType lastBranchReturned = null;
    for (Statement s : statements) {
      if (lastBranchReturned != null) {
        throw new BlockValidationException("unreachable statement: " + s);
      }
      lastBranchReturned = ValidationDispatch.dispatcher.apply(validator, s);
    }
    if (requiredReturnType != null) {
      if (lastBranchReturned == null) {
        throw new BlockValidationException("block may not return");
      }
      if (!lastBranchReturned.equals(requiredReturnType)) {
        throw new BlockValidationException(String.format("expected result type %s but found %s",
          requiredReturnType, lastBranchReturned));
      }
    }
  }

  static class ValidationDispatch {
    static final Dispatcher<ValidationDispatch, Statement, SimpleType> dispatcher =
      Dispatcher.builder(ValidationDispatch.class, Statement.class, SimpleType.class)
        .put(Statement.MethodInvoke.class, ValidationDispatch::methodInvoke)
        .put(Statement.ReturnStatement.class, ValidationDispatch::returnStat)
        .build();

    public SimpleType returnStat(Statement.ReturnStatement stat) {
      return stat.getExpression().resultType();
    }

    public SimpleType methodInvoke(@SuppressWarnings("unused") Statement.MethodInvoke stat) {
      return null;
    }
  }
}

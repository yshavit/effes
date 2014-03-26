package com.yuvalshavit.effes.compile;

import com.google.common.collect.ImmutableList;
import com.yuvalshavit.util.Dispatcher;
import org.antlr.v4.runtime.Token;

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

  public void validate(EfType requiredReturnType, CompileErrors errs) {
    ValidationDispatch validator = new ValidationDispatch();
    EfType lastBranchReturned = null;
    Statement lastStatement = null;
    for (Statement s : statements) {
      if (lastBranchReturned != null) {
        errs.add(s.token(), "unreachable statement: " + s);
      }
      lastBranchReturned = ValidationDispatch.dispatcher.apply(validator, s);
      lastStatement = s;
    }
    if (requiredReturnType != null) {
      Token token = lastStatement != null
        ? lastStatement.token()
        : null;
      if (lastBranchReturned == null) {
        errs.add(token, "block may not return, but needs to return " + requiredReturnType);
      } else if (!requiredReturnType.contains(lastBranchReturned)) {
        // e.g. return type (True | False), lastBranchReturned True
        String msg = String.format("expected result type %s but found %s", requiredReturnType, lastBranchReturned);
        errs.add(token, msg);
      }
    }
  }

  static class ValidationDispatch {
    static final Dispatcher<ValidationDispatch, Statement, EfType> dispatcher =
      Dispatcher.builder(ValidationDispatch.class, Statement.class, EfType.class)
        .put(Statement.MethodInvoke.class, ValidationDispatch::methodInvoke)
        .put(Statement.ReturnStatement.class, ValidationDispatch::returnStat)
        .build();

    public EfType returnStat(Statement.ReturnStatement stat) {
      return stat.getExpression().resultType();
    }

    public EfType methodInvoke(@SuppressWarnings("unused") Statement.MethodInvoke stat) {
      return null;
    }
  }
}

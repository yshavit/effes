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
  
  public int nVars() {
    return statements
      .stream()
      .map(Statement::var)
      .filter(v -> v != null)
      .mapToInt(EfVar::getArgPosition)
      .max()
      .orElse(-1)
      + 1;
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
    Token lastToken = null;
    for (Statement s : statements) {
      s.validate(errs);
      if (lastBranchReturned != null) {
        errs.add(s.token(), "unreachable statement: " + s);
      }
      lastBranchReturned = ValidationDispatch.dispatcher.apply(validator, s);
      lastToken = s.token();
    }
    if (requiredReturnType != null) {
      if (lastBranchReturned == null) {
        errs.add(lastToken, "block may not return, but needs to return " + requiredReturnType);
      } else if (!requiredReturnType.contains(lastBranchReturned)) {
        // e.g. return type (True | False), lastBranchReturned True
        String msg = String.format("expected result type %s but found %s", requiredReturnType, lastBranchReturned);
        errs.add(lastToken, msg);
      }
    }
  }

  static class ValidationDispatch {
    static final Dispatcher<ValidationDispatch, Statement, EfType> dispatcher =
      Dispatcher.builder(ValidationDispatch.class, Statement.class, EfType.class)
        .put(Statement.AssignStatement.class, ValidationDispatch::noReturn)
        .put(Statement.MethodInvoke.class, ValidationDispatch::noReturn)
        .put(Statement.ReturnStatement.class, ValidationDispatch::returnStat)
        .put(Statement.UnrecognizedStatement.class, ValidationDispatch::noReturn)
        .build();

    public EfType returnStat(Statement.ReturnStatement stat) {
      return stat.getExpression().resultType();
    }

    public EfType noReturn(@SuppressWarnings("unused") Statement stat) {
      return null;
    }
  }
}

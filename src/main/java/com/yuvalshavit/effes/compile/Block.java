package com.yuvalshavit.effes.compile;

import com.google.common.collect.ImmutableList;
import org.antlr.v4.runtime.Token;

import java.util.List;

public final class Block extends Node {

  private final List<Statement> statements;

  public Block(Token token, List<Statement> statements) {
    super(token, computeType(statements));
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
  public String toString() {
    int nStats = statements.size();
    return String.format("<block with %d statement%s>", nStats, nStats != 1 ? "s" : "");
  }

  @Override
  public void state(NodeStateListener out) {
    statements.forEach(s -> s.state(out));
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

  private static EfType computeType(List<Statement> statements) {
    for (Statement s : statements) {
      EfType resultType = s.resultType();
      if (!EfType.VOID.equals(resultType)) {
        return resultType;
      }
    }
    return EfType.VOID;
  }

  @Override
  public void validate(CompileErrors errs) {
    EfType lastStatementReturned = EfType.VOID;
    for (Statement s : statements) {
      s.validate(errs);
      if (!EfType.VOID.equals(lastStatementReturned)) {
        errs.add(s.token(), "unreachable statement: " + s);
      }
      lastStatementReturned = s.resultType();
    }
  }

  public void validateResultType(EfType requiredReturnType, CompileErrors errs) {
    EfType actualReturnType = resultType();
    if (EfType.VOID.equals(requiredReturnType)) {
      if (!EfType.VOID.equals(actualReturnType)) {
        errs.add(token(), String.format("expected no result type, but found %s", actualReturnType));
      }
    } else {
      if (EfType.VOID.equals(actualReturnType)) {
        errs.add(token(), "block may not return, but needs to return " + requiredReturnType);
      } else if (!requiredReturnType.contains(actualReturnType)) {
        // e.g. return type (True | False), lastBranchReturned True
        String msg = String.format("expected result type %s but found %s", requiredReturnType, actualReturnType);
        errs.add(token(), msg);
      }
    }
  }
}

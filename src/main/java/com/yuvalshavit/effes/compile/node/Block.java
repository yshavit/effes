package com.yuvalshavit.effes.compile.node;

import com.google.common.collect.ImmutableList;
import com.yuvalshavit.effes.compile.NodeStateListener;
import com.yuvalshavit.effes.compile.NodeStateVisitor;
import org.antlr.v4.runtime.Token;

import java.util.List;

public final class Block extends Node {

  private final List<Statement> statements;

  public Block(Token token, List<Statement> statements) {
    this(new BlockInfo(token, statements), statements);
  }

  private Block(BlockInfo info, List<Statement> statements) {
    super(info.token, info.type);
    this.statements = ImmutableList.copyOf(statements);
  }

  @Override
  public boolean elideFromState() {
    return true;
  }

  public List<Statement> statements() {
    return statements;
  }

  public int nVars() {
    class MaxArgFinder implements NodeStateListener {
      int pos = -1;

      @Override
      public void child(Node child) {
        EfVar var = child.var();
        if (var != null) {
          pos = Math.max(pos, var.getArgPosition());
        }
      }
    }
    MaxArgFinder argFinder = new MaxArgFinder();
    NodeStateListener.accept(this, argFinder);
    return argFinder.pos + 1;
  }

  @Override
  public String toString() {
    int nStats = statements.size();
    return String.format("<block with %d statement%s>", nStats, nStats != 1 ? "s" : "");
  }

  @Override
  public void state(NodeStateVisitor out) {
    statements.forEach(out::visitChild);
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

  @Override
  public void validate(CompileErrors errs) {
    EfType lastStatementReturned = EfType.VOID;
    for (Statement s : statements) {
      if (!EfType.VOID.equals(lastStatementReturned)) {
        errs.add(s.token(), "unreachable statement: " + s);
      }
      lastStatementReturned = s.resultType();
    }
  }

  public void validateResultType(EfType requiredReturnType, CompileErrors errs) {
    if (token() == null) {
      // This means there's no body; this will have been checked and reported on by MethodsFinder
      return;
    }
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

  private static class BlockInfo {
    private final Token token;
    private final EfType type;

    public BlockInfo(Token token, List<Statement> statements) {
      // We'll use the incoming token as a backup, but really the identifying token for this block is the statement
      // that first establishes (guarantees) the block's return. That's the token that we'll want to see in error
      // messages. For instance, if the block should return Foo but actually returns Bar, we'll want the reported token
      // to be the one for the return statement, not for the start of the block.
      EfType type = EfType.VOID;
      for (Statement s : statements) {
        EfType statementType = s.resultType();
        if (!EfType.VOID.equals(statementType)) {
          type = statementType;
          token = s.token();
          break;
        }
      }
      this.token = token;
      this.type = type;
    }
  }
}

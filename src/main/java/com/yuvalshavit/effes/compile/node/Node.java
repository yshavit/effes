package com.yuvalshavit.effes.compile.node;

import com.yuvalshavit.effes.compile.NodeStateVisitor;
import org.antlr.v4.runtime.Token;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class Node {

  private final Token token;
  private final EfType resultType;

  protected Node(Token token, @Nonnull EfType resultType) {
    this.token = token;
    this.resultType = resultType;
  }

  @Nullable
  public EfVar var() {
    return null;
  }

  public Token token() {
    return token;
  }

  public boolean elideFromState() {
    return false;
  }

  @Nonnull
  public EfType resultType() {
    return resultType;
  }

  @Override
  public abstract String toString(); // force overrides
  public abstract void validate(CompileErrors errs);
  /**
   * Describes this node's state; used for debugging, toString, etc.
   * @param out the state visitor
   */
  public abstract void state(NodeStateVisitor out);
}

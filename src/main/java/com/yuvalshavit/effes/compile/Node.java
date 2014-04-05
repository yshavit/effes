package com.yuvalshavit.effes.compile;

import org.antlr.v4.runtime.Token;

import javax.annotation.Nonnull;

public abstract class Node {

  private final Token token;
  private final EfType resultType;

  protected Node(Token token, @Nonnull EfType resultType) {
    this.token = token;
    this.resultType = resultType;
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
  public abstract void state(NodeStateListener out);
}

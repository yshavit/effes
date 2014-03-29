package com.yuvalshavit.effes.compile;

import org.antlr.v4.runtime.Token;

abstract class Node {

  private final Token startToken;

  protected Node(Token startToken) {
    this.startToken = startToken;
  }

  public Token token() {
    return token();
  }

  @Override
  public abstract String toString(); // force overrides
  public abstract void validate(CompileErrors errs);
}

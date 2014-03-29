package com.yuvalshavit.effes.compile;

import org.antlr.v4.runtime.Token;

public abstract class Node {

  private final Token token;

  protected Node(Token token) {
    this.token = token;
  }

  public Token token() {
    return token;
  }

  @Override
  public abstract String toString(); // force overrides
  public abstract void validate(CompileErrors errs);
}

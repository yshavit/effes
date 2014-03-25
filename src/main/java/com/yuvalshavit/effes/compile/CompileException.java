package com.yuvalshavit.effes.compile;

import org.antlr.v4.runtime.Token;

public class CompileException extends RuntimeException {
  private final int line;
  private final int column;
  public CompileException(String msg, Token at) {
    super(String.format("%s at \"%s\" (line %d, column %d)", msg, at.getText(), at.getLine(), at.getCharPositionInLine()));
    this.line = at.getLine();
    this.column = at.getCharPositionInLine();
  }

  public int getLine() {
    return line;
  }

  public int getColumn() {
    return column;
  }
}

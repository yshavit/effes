package com.yuvalshavit.effes.parser;

public class AntlrParseException extends RuntimeException {
  public AntlrParseException(int line, int posInLine, String msg, Throwable cause) {
    // posInLine comes in 0-indexed, but we want to 1-index it so it lines up with what editors say (they
    // tend to 1-index)
    super(String.format("at line %d column %d: %s", line, posInLine+1, msg), cause);
  }
}

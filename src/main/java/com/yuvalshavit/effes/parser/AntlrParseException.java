package com.yuvalshavit.effes.parser;

public class AntlrParseException extends RuntimeException {

  private static final long serialVersionUID = -4308604862865727621L;

  public AntlrParseException(int line, int posInLine, String msg, Throwable cause) {
    // posInLine comes in 0-indexed, but we want to 1-index it so it lines up with what editors say (they
    // tend to 1-index)
    super(String.format("at line %d column %d: %s", line, posInLine+1, msg), cause);
  }
}

package com.yuvalshavit.effes.compile;

import org.antlr.v4.runtime.Token;

public final class ParseException extends CompilationException {
  public ParseException(Token token, String message) {
    super(token, message);
  }
}

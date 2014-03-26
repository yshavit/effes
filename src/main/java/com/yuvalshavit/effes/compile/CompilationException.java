package com.yuvalshavit.effes.compile;

import org.antlr.v4.runtime.Token;

public abstract class CompilationException extends RuntimeException {
  protected CompilationException(Token token, String message) {
    super(formatMessage(token, message));
  }

  private static String formatMessage(Token token, String message) {
    if (token == null) {
      return message;
    }
    String tokenText = token.getText().trim();
    String tokenTextDelim = tokenText.isEmpty()
      ? ""
      : ": ";
    return String.format("%s (at line %d, column %d%s%s)",
      message, token.getLine(), token.getCharPositionInLine(), tokenTextDelim, tokenText);
  }
}

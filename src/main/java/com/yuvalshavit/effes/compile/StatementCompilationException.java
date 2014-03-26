package com.yuvalshavit.effes.compile;

import org.antlr.v4.runtime.Token;

public final class StatementCompilationException extends CompilationException {
  public StatementCompilationException(Token token, String message) {
    super(token, message);
  }
}

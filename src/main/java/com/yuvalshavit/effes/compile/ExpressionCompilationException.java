package com.yuvalshavit.effes.compile;

import org.antlr.v4.runtime.Token;

public final class ExpressionCompilationException extends CompilationException {
  public ExpressionCompilationException(Token token, String message) {
    super(token, message);
  }
}

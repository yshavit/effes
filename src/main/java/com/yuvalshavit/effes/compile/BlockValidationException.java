package com.yuvalshavit.effes.compile;

import org.antlr.v4.runtime.Token;

public final class BlockValidationException extends CompilationException {
  public BlockValidationException(Token token, String message) {
    super(token, message);
  }
}

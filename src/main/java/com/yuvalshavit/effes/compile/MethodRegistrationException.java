package com.yuvalshavit.effes.compile;

import org.antlr.v4.runtime.Token;

public class MethodRegistrationException extends CompilationException {
  public MethodRegistrationException(Token token, String message) {
    super(token, message);
  }
}

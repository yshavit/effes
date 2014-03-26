package com.yuvalshavit.effes.compile;

import org.antlr.v4.runtime.Token;

public class TypeRegistryException extends CompilationException {
  public TypeRegistryException(Token token, String message) {
    super(token, message);
  }
}

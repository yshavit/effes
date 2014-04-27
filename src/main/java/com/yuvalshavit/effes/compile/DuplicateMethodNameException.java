package com.yuvalshavit.effes.compile;

public final class DuplicateMethodNameException extends RuntimeException {
  private static final long serialVersionUID = 8951803540367740805L;

  public DuplicateMethodNameException(MethodId name) {
    super("duplicate method name: " + name);
  }
}

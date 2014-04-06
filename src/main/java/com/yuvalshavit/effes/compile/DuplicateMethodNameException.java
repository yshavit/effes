package com.yuvalshavit.effes.compile;

public final class DuplicateMethodNameException extends RuntimeException {
  public DuplicateMethodNameException(MethodId name) {
    super("duplicate method name: " + name);
  }
}

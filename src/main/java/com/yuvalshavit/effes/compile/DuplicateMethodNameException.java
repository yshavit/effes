package com.yuvalshavit.effes.compile;

public final class DuplicateMethodNameException extends RuntimeException {
  public DuplicateMethodNameException(String name) {
    super("duplicate method name: " + name);
  }
}

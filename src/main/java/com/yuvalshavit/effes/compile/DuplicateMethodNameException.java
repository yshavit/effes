package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.compile.node.MethodId;

public final class DuplicateMethodNameException extends RuntimeException {
  private static final long serialVersionUID = 8951803540367740805L;

  public DuplicateMethodNameException(MethodId name) {
    super("duplicate method name: " + name);
  }
}

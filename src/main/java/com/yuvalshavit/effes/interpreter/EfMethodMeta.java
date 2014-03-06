package com.yuvalshavit.effes.interpreter;

import java.util.List;

public final class EfMethodMeta {
  private final EfMethod method;
  private final EfType returnType;
  private final List<EfType> args;

  public EfMethodMeta(EfMethod method, EfType returnType, List<EfType> args) {
    this.method = method;
    this.returnType = returnType;
    this.args = args;
  }

  public EfMethod getMethod() {
    return method;
  }

  public EfType getReturnType() {
    return returnType;
  }

  public List<EfType> getArgs() {
    return args;
  }
}

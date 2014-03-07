package com.yuvalshavit.effes.base;

import javax.annotation.Nullable;
import java.util.List;

public final class EfMethodMeta {

  private final EfMethod method;
  private final Type returnType;
  private final List<Type> args;

  public EfMethodMeta(@Nullable EfMethod method, Type returnType, List<Type> args) {
    this.method = method;
    this.returnType = returnType;
    this.args = args;
  }

  @Nullable
  public EfMethod getMethod() {
    return method;
  }

  public Type getReturnType() {
    return returnType;
  }

  public List<Type> getArgs() {
    return args;
  }
}

package com.yuvalshavit.effes.base;

import com.yuvalshavit.effes.ir.Block;

import javax.annotation.Nullable;
import java.util.List;

public final class EfMethodMeta {

  private final EfMethod method;
  private final Type returnType;
  private final List<Type> argTypes;
  private final Block body;

  public EfMethodMeta(@Nullable EfMethod method, Type returnType, List<Type> argTypes, Block body) {
    this.method = method;
    this.returnType = returnType;
    this.argTypes = argTypes;
    this.body = body;
  }

  @Nullable
  public EfMethod getMethod() {
    return method;
  }

  public Type getReturnType() {
    return returnType;
  }

  public List<Type> getArgTypes() {
    return argTypes;
  }

  public Block getBody() {
    return body;
  }
}

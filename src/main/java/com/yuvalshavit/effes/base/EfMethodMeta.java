package com.yuvalshavit.effes.base;

import com.yuvalshavit.effes.ir.Block;

import javax.annotation.Nullable;
import java.util.List;

public final class EfMethodMeta {

  private final Type returnType;
  private final List<Type> argTypes;
  private final Block body;

  public EfMethodMeta(Type returnType, List<Type> argTypes, @Nullable Block body) {
    this.returnType = returnType;
    this.argTypes = argTypes;
    this.body = body;
  }

  public Type getReturnType() {
    return returnType;
  }

  public List<Type> getArgTypes() {
    return argTypes;
  }

  @Nullable
  public Block getBody() {
    return body;
  }
}

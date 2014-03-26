package com.yuvalshavit.effes.compile;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.function.Function;

public final class EfMethod<B> {
  private final List<EfType.SimpleType> argTypes;
  private final EfType.SimpleType resultType;
  private final B body;

  public EfMethod(List<EfType.SimpleType> argTypes, EfType.SimpleType resultType, B body) {
    this.argTypes = ImmutableList.copyOf(argTypes);
    this.resultType = resultType;
    this.body = body;
  }

  public List<EfType.SimpleType> getArgTypes() {
    return argTypes;
  }

  public EfType.SimpleType getResultType() {
    return resultType;
  }

  public B getBody() {
    return body;
  }

  public <B2> EfMethod<B2> tranform(Function<? super B, ? extends B2> func) {
    return new EfMethod<>(argTypes, resultType, func.apply(body));
  }

  @Override
  public String toString() {
    return String.format("(%s) -> %s", Joiner.on(", ").join(argTypes), resultType);
  }
}

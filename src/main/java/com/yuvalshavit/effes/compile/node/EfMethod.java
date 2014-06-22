package com.yuvalshavit.effes.compile.node;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.function.Function;

public final class EfMethod<B> {
  private final List<String> genericParams;
  private final EfArgs args;
  private final EfType resultType;
  private final B body;

  public EfMethod(List<String> genericParams, EfArgs args, EfType resultType, B body) {
    this.genericParams = ImmutableList.copyOf(genericParams);
    this.args = args;
    this.resultType = resultType;
    this.body = body;
  }

  public List<String> getGenericParams() {
    return genericParams;
  }

  public EfArgs getArgs() {
    return args;
  }

  public EfType getResultType() {
    return resultType;
  }

  public B getBody() {
    return body;
  }

  public <B2> EfMethod<B2> tranform(Function<? super EfMethod<? extends B>, ? extends B2> func) {
    return new EfMethod<>(genericParams, args, resultType, func.apply(this));
  }

  @Override
  public String toString() {
    return String.format("%s -> %s", args, resultType);
  }
}

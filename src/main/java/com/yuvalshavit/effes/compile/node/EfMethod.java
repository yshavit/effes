package com.yuvalshavit.effes.compile.node;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class EfMethod<B> {
  private final List<String> generics;
  private final EfArgs args;
  private final EfType resultType;
  private final B body;

  public EfMethod(List<String> generics, EfArgs args, EfType resultType, B body) {
    this.generics = ImmutableList.copyOf(generics);
    this.args = args;
    this.resultType = resultType;
    this.body = body;
  }

  public EfArgs getArgs() {
    return args;
  }

  public EfType getResultType() {
    return resultType;
  }

  public List<EfType.GenericType> getGenerics() {
    return generics.stream().map(g -> new EfType.GenericType(g, this)).collect(Collectors.toList());
  }

  public B getBody() {
    return body;
  }

  public <B2> EfMethod<B2> tranform(Function<? super EfMethod<? extends B>, ? extends B2> func) {
    return new EfMethod<>(generics, args, resultType, func.apply(this));
  }

  public EfMethod<B> reify(Function<EfType.GenericType, EfType> reification) {
    EfType reifiedRv = getResultType().reify(reification); // TODO could same-named generics get confused in e.g. Foo[One[Bar]] where Foo[T] and One[T] reuse the same?
    EfArgs reifiedArgs = getArgs().reify(reification);
    return new EfMethod<>(generics, reifiedArgs, reifiedRv, getBody());
  }

  @Override
  public String toString() {
    return String.format("%s -> %s", args, resultType);
  }
}

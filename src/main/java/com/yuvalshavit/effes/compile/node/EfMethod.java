package com.yuvalshavit.effes.compile.node;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.function.Function;

public final class EfMethod<B> {
  private final Id trueId;
  private final List<EfType.GenericType> generics;
  private final EfArgs args;
  private final EfType resultType;
  private final B body;

  public EfMethod(Id trueId, List<EfType.GenericType> generics, EfArgs args, EfType resultType, B body) {
    this.trueId = Preconditions.checkNotNull(trueId);
    this.generics = ImmutableList.copyOf(generics);
    this.args = args;
    this.resultType = resultType;
    this.body = body;
    this.getGenerics().forEach(g -> {
      if (!this.trueId.equals(g.getOwner())) {
        throw new IllegalArgumentException("trueId mismatch");
      }
    });
  }

  public EfArgs getArgs() {
    return args;
  }

  public EfType getResultType() {
    return resultType;
  }

  public List<EfType.GenericType> getGenerics() {
    return generics;
  }

  public B getBody() {
    return body;
  }

  public <B2> EfMethod<B2> tranform(Function<? super EfMethod<? extends B>, ? extends B2> func) {
    return new EfMethod<>(trueId, generics, args, resultType, func.apply(this));
  }

  public EfMethod<B> reify(Function<EfType.GenericType, EfType> reification) {
    EfType reifiedRv = getResultType().reify(reification); // TODO could same-named generics get confused in e.g. Foo[One[Bar]] where Foo[T] and One[T] reuse the same?
    EfArgs reifiedArgs = getArgs().reify(reification);
    return new EfMethod<>(trueId, generics, reifiedArgs, reifiedRv, getBody());
  }

  @Override
  public String toString() {
    return String.format("%s -> %s", args, resultType);
  }

  public Id getTrueId() {
    return trueId;
  }

  public static class Id {
    private final String name;

    public Id(String name) {
      this.name = Preconditions.checkNotNull(name);
    }

    @Override
    public String toString() {
      return name;
    }
  }
}

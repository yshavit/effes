package com.yuvalshavit.effes.interpreter;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class EfMethodMeta {
  static final EfType owningType = new EfType();

  private final EfMethod method;
  private final EfType returnType;
  private final List<EfType> args;

  public EfMethodMeta(EfMethod method, EfType returnType, List<EfType> args) {
    this.method = method;
    this.returnType = returnType;
    this.args = args;
  }
  
  public EfMethodMeta resolveOwningType(EfType type) {
    Function<EfType, EfType> resolver = t -> (t==owningType ? type : t);
    List<EfType> resolvedArgs = ImmutableList.copyOf(args.stream().map(resolver).collect(Collectors.toList()));
    return new EfMethodMeta(method, resolver.apply(returnType), resolvedArgs);
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

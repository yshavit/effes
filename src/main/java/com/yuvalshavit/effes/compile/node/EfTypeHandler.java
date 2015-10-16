package com.yuvalshavit.effes.compile.node;

public interface EfTypeHandler<T> {
  T simple(EfType.SimpleType type);
  T disjunction(EfType.DisjunctiveType type);
  T generic(EfType.GenericType type);
  T unknown();
  T voidType();

  static <T> EfTypeHandlerBuilders.SimpleHandler<T> create(Class<T> resultClass) {
    return EfTypeHandlerBuilders.create(resultClass);
  }
}

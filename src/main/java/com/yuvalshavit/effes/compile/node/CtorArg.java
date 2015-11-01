package com.yuvalshavit.effes.compile.node;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.function.Function;
import java.util.function.Supplier;

import com.yuvalshavit.util.Equality;
import com.yuvalshavit.util.Lazy;

public class CtorArg {
  private static final Equality<CtorArg> equality = Equality.forClass(CtorArg.class)
    .with("pos", CtorArg::getArgPosition)
    .with("name", CtorArg::name)
    .with("type", CtorArg::type)
    .exactClassOnly();
  private final int pos;
  private final String name;
  private final Lazy<EfType> type;

  public CtorArg(int pos, String name, Supplier<EfType> type) {
    this.name = checkNotNull(name, "name");
    this.type = Lazy.lazy(type);
    this.pos = pos;
  }
  
  public CtorArg(int pos, String name, EfType type) {
    this(pos, name, Lazy.forced(type));
  }

  public String name() {
    return name;
  }

  public EfType type() {
    return type.get();
  }
  
  public CtorArg reify(Function<EfType.GenericType, EfType> reification) {
    return new CtorArg(pos, name, type.transform(t -> t.reify(reification)));
  }

  public CtorArg castTo(EfType newArg) {
    return new CtorArg(pos, name, newArg);
  }

  public int getArgPosition() {
    return pos;
  }

  @Override
  public String toString() {
    return String.format("[%d] %s: %s", pos, name, type);
  }

  @Override
  public int hashCode() {
    return equality.hash(this);
  }

  @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
  @Override
  public boolean equals(Object obj) {
    return equality.areEqual(this, obj);
  }
}

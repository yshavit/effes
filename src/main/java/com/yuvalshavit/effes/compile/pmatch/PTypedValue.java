package com.yuvalshavit.effes.compile.pmatch;

import java.util.List;
import java.util.function.Function;

import com.yuvalshavit.effes.compile.node.EfType;

public abstract class PTypedValue<T> {
  private final EfType.SimpleType type;
  
  private PTypedValue(EfType.SimpleType type) {
    this.type = type;
  }

  public EfType.SimpleType type() {
    return type;
  }
  
  public <R> PTypedValue<R> with(List<R> newValues) {
    return handle(
      l -> {
        @SuppressWarnings("unchecked")
        PTypedValue<R> casted = (PTypedValue<R>) l;
        return casted;
      },
      v -> new StandardValue<>(type, newValues));
  }

  public abstract <R> R handle(
    Function<? super LargeDomainValue<T>, ? extends R> whenLargeDomainValue,
    Function<? super StandardValue<T>, ? extends R> whenStandardValue);

  /**
   * A value with a "large" domain, which makes it unfeasible (or unhelpful) to actually look at its values.
   * 
   * These are all built-in types (numbers and chars).
   */
  public static class LargeDomainValue<T> extends PTypedValue<T> {
    public LargeDomainValue(EfType.SimpleType type) {
      super(type);
    }

    @Override
    public <R> R handle(
      Function<? super LargeDomainValue<T>, ? extends R> whenLargeDomainValue,
      Function<? super StandardValue<T>, ? extends R> whenStandardValue)
    {
      return whenLargeDomainValue.apply(this);
    }
  }

  /**
   * A standard, userland-defined value -- one whose type is a non-builtin type, or even the simpler builtin types
   * like True and False.
   */
  public static class StandardValue<T> extends PTypedValue<T> {
    private final List<T> args;

    public StandardValue(EfType.SimpleType type, List<T> args) {
      super(type);
      this.args = args;
    }

    public List<T> args() {
      return args;
    }

    @Override
    public <R> R handle(
      Function<? super LargeDomainValue<T>, ? extends R> whenLargeDomainValue,
      Function<? super StandardValue<T>, ? extends R> whenStandardValue)
    {
      return whenStandardValue.apply(this);
    }
  }
}

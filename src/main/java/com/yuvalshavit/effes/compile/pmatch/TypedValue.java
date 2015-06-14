package com.yuvalshavit.effes.compile.pmatch;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.yuvalshavit.effes.compile.node.EfType;

public abstract class TypedValue<T> {
  private final EfType.SimpleType type;
  
  private TypedValue(EfType.SimpleType type) {
    this.type = type;
  }

  public EfType.SimpleType type() {
    return type;
  }
  
  public TypedValue<T> with(List<T> newValues) {
    return transform(
      Function.identity(),
      v -> new StandardValue<>(type, newValues));
  }

  public abstract <R> R transform(
    Function<? super LargeDomainValue<T>, ? extends R> whenLargeDomainValue,
    Function<? super StandardValue<T>, ? extends R> whenStandardValue);


  public void consume(Consumer<? super LargeDomainValue<T>> whenLargeDomainValue, Consumer<? super StandardValue<T>> whenStandardValue) {
    transform(
      l -> {
        whenLargeDomainValue.accept(l);
        return null;
      },
      s -> {
        whenStandardValue.accept(s);
        return null;
      });
  }

  /**
   * A value with a "large" domain, which makes it unfeasible (or unhelpful) to actually look at its values.
   * 
   * These are all built-in types (numbers and chars).
   */
  public static class LargeDomainValue<T> extends TypedValue<T> {
    public LargeDomainValue(EfType.SimpleType type) {
      super(type);
    }

    @Override
    public <R> R transform(
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
  public static class StandardValue<T> extends TypedValue<T> {
    private final List<T> args;

    public StandardValue(EfType.SimpleType type, List<T> args) {
      super(type);
      this.args = args;
    }

    public List<T> args() {
      return args;
    }

    @Override
    public <R> R transform(
      Function<? super LargeDomainValue<T>, ? extends R> whenLargeDomainValue,
      Function<? super StandardValue<T>, ? extends R> whenStandardValue)
    {
      return whenStandardValue.apply(this);
    }
  }
}

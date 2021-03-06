package com.yuvalshavit.effes.compile.pmatch;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.google.common.base.Joiner;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.util.EfFunctions;

public abstract class TypedValue<T> {
  private final EfType.SimpleType type;
  
  private TypedValue(EfType.SimpleType type) {
    this.type = type;
  }

  public EfType.SimpleType type() {
    return type;
  }

  public abstract <R> R transform(
    Function<? super LargeDomainValue<T>, ? extends R> whenLargeDomainValue,
    Function<? super StandardValue<T>, ? extends R> whenStandardValue);
  
  public void consume(Consumer<? super LargeDomainValue<T>> whenLargeDomainValue, Consumer<? super StandardValue<T>> whenStandardValue) {
    transform(EfFunctions.toFunction(whenLargeDomainValue), EfFunctions.toFunction(whenStandardValue));
  }
  
  @Override public abstract String toString();

  /**
   * A value with a "large" domain, which makes it unfeasible (or unhelpful) to actually look at its values.
   * 
   * These are all built-in types (numbers and chars).
   */
  public static class LargeDomainValue<T> extends TypedValue<T> {
    private final boolean exhaustive;
    public LargeDomainValue(EfType.SimpleType type, boolean exhaustive) {
      super(type);
      this.exhaustive = exhaustive;
    }

    /** whether this value exhausts all possibilities. For instance, <tt>IntValue</tt> is exhaustive, while <tt>27</tt> is not. */
    public boolean isExhaustive() {
      return exhaustive;
    }

    @Override
    public <R> R transform(
      Function<? super LargeDomainValue<T>, ? extends R> whenLargeDomainValue,
      Function<? super StandardValue<T>, ? extends R> whenStandardValue)
    {
      return whenLargeDomainValue.apply(this);
    }

    @Override
    public String toString() {
      return String.format("%s (%s)", type().getName(), exhaustive ? "exhaustive" : "point");
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

    @Override
    public String toString() {
      return type().getName() + '(' + Joiner.on(", ").join(args) + ')'; 
    }
  }
}

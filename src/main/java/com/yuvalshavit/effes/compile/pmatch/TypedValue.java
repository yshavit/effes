package com.yuvalshavit.effes.compile.pmatch;

import java.util.List;
import java.util.function.Function;

import com.yuvalshavit.effes.compile.node.EfType;

public abstract class TypedValue {
  private final EfType.SimpleType type;
  
  private TypedValue(EfType.SimpleType type) {
    this.type = type;
  }

  public EfType.SimpleType type() {
    return type;
  }
  
  public abstract <T> T handle(
    Function<? super LargeDomainValue, ? extends T> whenLargeDomainValue,
    Function<? super StandardValue, ? extends T> whenStandardValue);

  /**
   * A value with a "large" domain, which makes it unfeasible (or unhelpful) to actually look at its values.
   * 
   * These are all built-in types (numbers and chars).
   */
  public static class LargeDomainValue extends TypedValue {
    public LargeDomainValue(EfType.SimpleType type) {
      super(type);
    }

    @Override
    public <T> T handle(
      Function<? super LargeDomainValue, ? extends T> whenLargeDomainValue,
      Function<? super StandardValue, ? extends T> whenStandardValue)
    {
      return whenLargeDomainValue.apply(this);
    }
  }

  /**
   * A standard, userland-defined value -- one whose type is a non-builtin type, or even the simpler builtin types
   * like True and False.
   */
  public static class StandardValue extends TypedValue {
    private final List<TypedValue> args;

    public StandardValue(EfType.SimpleType type, List<TypedValue> args) {
      super(type);
      this.args = args;
    }

    public List<TypedValue> args() {
      return args;
    }

    @Override
    public <T> T handle(
      Function<? super LargeDomainValue, ? extends T> whenLargeDomainValue,
      Function<? super StandardValue, ? extends T> whenStandardValue)
    {
      return whenStandardValue.apply(this);
    }
  }
}

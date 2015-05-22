package com.yuvalshavit.effes.compile.patternmatch;

import com.google.common.base.Preconditions;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.util.Equality;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class MatchAlternative {
  private static final MatchAlternative unnamed = new WildcardAlternative("_");

  private MatchAlternative() {}

  public static MatchAlternative unnamed() {
    return unnamed;
  }

  @Nullable public abstract Input mergeInto(Input remainingInputs);

  public static class SimpleTypeAlternative extends MatchAlternative {

    private static final Equality<SimpleTypeAlternative> equality = Equality
      .forClass(SimpleTypeAlternative.class)
      .with(SimpleTypeAlternative::type)
      .exactClassOnly();

    private final EfType.SimpleType type;
    private final List<Input> ctorArgs;

    public SimpleTypeAlternative(EfType.SimpleType type, List<Input> ctorArgs) {
      this.type = Preconditions.checkNotNull(type, "type");
      this.ctorArgs = Preconditions.checkNotNull(ctorArgs, "ctor args");
    }

    public EfType.SimpleType type() {
      return type;
    }

    @Override
    public int hashCode() {
      return equality.hash(this);
    }

    @Override
    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    public boolean equals(Object obj) {
      return equality.areEqual(this, obj);
    }

    @Override
    public String toString() {
      return type().toString();
    }
  }

  public static class ValueAlternative extends MatchAlternative {
    private static final Equality<ValueAlternative> equality = Equality
      .forClass(ValueAlternative.class)
      .with(ValueAlternative::value)
      .exactClassOnly();

    private final EfType.SimpleType type;
    private final Object value;

    public ValueAlternative(EfType.SimpleType type, Object value) {
      this.type = Preconditions.checkNotNull(type, "type");
      this.value = Preconditions.checkNotNull(value, "value");
    }

    private Object value() {
      return value;
    }

    @Nullable
    @Override
    public Input mergeInto(Input remainingInputs) {
      if (remainingInputs.containsType(type) && !input.contains(this)) {
        return input.add(this);
      } else {
        return null;
      }
    }

    @Override
    public int hashCode() {
      return equality.hash(this);
    }

    @Override
    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    public boolean equals(Object obj) {
      return equality.areEqual(this, obj);
    }

    @Override
    public String toString() {
      return value.toString();
    }
  }

  public static class WildcardAlternative extends MatchAlternative {
    private final String name;

    public WildcardAlternative(String name) {
      this.name = Preconditions.checkNotNull(name, "name");
    }

    @Nullable
    @Override
    public Input mergeInto(Input remainingInputs) {
      if (remainingInputs.isEmpty()) {
        return null;
      }
      return remainingInputs.clear();
    }

    @Override
    public String toString() {
      return name;
    }
  }
}

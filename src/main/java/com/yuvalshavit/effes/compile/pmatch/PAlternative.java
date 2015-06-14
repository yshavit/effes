package com.yuvalshavit.effes.compile.pmatch;

import com.yuvalshavit.util.Equality;

public abstract class PAlternative {
  private PAlternative() {}
  
  @Override public abstract int hashCode();
  @Override public abstract boolean equals(Object other);
  @Override public abstract String toString();
  
  public static class Any extends PAlternative {
    private static final Equality<Any> equality = Equality.forClass(Any.class).with("name", Any::name).exactClassOnly();
    private final String name;

    public Any(String name) {
      this.name = name;
    }

    private String name() {
      return name;
    }

    @Override
    public String toString() {
      return equality.toString(this);
    }

    @Override
    public int hashCode() {
      return equality.hash(this);
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object other) {
      return equality.areEqual(this, other);
    }
  }
  
  public static class Simple extends PAlternative {
    private static final Equality<Simple> equality = Equality.forClass(Simple.class).with("value", Simple::value).exactClassOnly();
    private final PTypedValue<PAlternative> value;

    public Simple(PTypedValue<PAlternative> value) {
      this.value = value;
    }

    public PTypedValue<PAlternative> value() {
      return value;
    }

    @Override
    public int hashCode() {
      return equality.hash(this);
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object other) {
      return equality.areEqual(this, other);
    }

    @Override
    public String toString() {
      return equality.toString(this);
    }
  }
}

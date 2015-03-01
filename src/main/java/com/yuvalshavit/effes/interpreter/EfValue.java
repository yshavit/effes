package com.yuvalshavit.effes.interpreter;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.yuvalshavit.effes.compile.node.EfType;

import java.util.List;

public class EfValue {
  private final EfType.SimpleType type;
  private final List<EfValue> state;

  public static EfValue of(EfType.SimpleType type, List<EfValue> state) {
    return new EfValue(type, state);
  }

  public static EfValue of(EfType.SimpleType type, String value) {
    return new EfStringValue(type, value);
  }

  public EfValue(EfType.SimpleType type, List<EfValue> state) {
    this.type = type;
    // TODO also check types against expected
    this.state = ImmutableList.copyOf(state);
  }

  public EfType.SimpleType getType() {
    return type;
  }

  public List<EfValue> getState() {
    return state;
  }

  @Override
  public String toString() {
    if (state.isEmpty()) {
      return type.getName();
    }
    StringBuilder sb = new StringBuilder(type.getName()).append('(');
    Joiner.on(", ").appendTo(sb, state);
    sb.append(')');
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    EfValue efValue = (EfValue) o;
    return state.equals(efValue.state) && type.equals(efValue.type);

  }

  @Override
  public int hashCode() {
    int result = type.hashCode();
    result = 31 * result + state.hashCode();
    return result;
  }

  public static class EfStringValue extends EfValue {
    private final String value;

    private EfStringValue(EfType.SimpleType type, String value) {
      super(type, ImmutableList.of());
      this.value = value;
    }

    public String getString() {
      return value;
    }

    @Override
    public String toString() {
      return String.format("%s(%s)", getType(), value.replace("(", "\\(").replace(")", "\\)"));
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;

      EfStringValue that = (EfStringValue) o;

      return value.equals(that.value);
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + value.hashCode();
      return result;
    }
  }
}

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
  
  public static EfValue of(String value) {
    throw new UnsupportedOperationException(""); // TODO
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
}

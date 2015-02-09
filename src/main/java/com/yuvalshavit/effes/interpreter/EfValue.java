package com.yuvalshavit.effes.interpreter;

import com.google.common.collect.ImmutableList;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.effes.compile.node.EfVar;

import java.util.List;

public class EfValue {
  private final EfType.SimpleType type;
  private final List<EfValue> state;

  public static EfValue of(EfType.SimpleType type, List<EfValue> state) {
    if (state.size() != type.getCtorArgs().size()) {
      throw new IllegalArgumentException(String.format("%s has %d arg(s), but %d given: %s",
        type, type.getCtorArgs().size(), state.size(), state ));
    }
    // TODO also check types against expected
    return new EfValue(type, state);
  }

  public static EfValue of(EfType.SimpleType type, String value) {
    return new EfStringValue(type, value);
  }

  public EfValue(EfType.SimpleType type, List<EfValue> state) {
    this.type = type;
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
    if (type.getCtorArgs().isEmpty()) {
      return type.getName();
    }
    StringBuilder sb = new StringBuilder(type.getName()).append('(');
    for (int i = 0; i < type.getCtorArgs().size(); ++i) {
      EfVar efVar = type.getCtorArgs().get(i);
      EfValue arg = state.get(i);
      // "name: SomeValue", where SomeValue is paren'ed iff it has args
      boolean parens = ! arg.getType().getCtorArgs().isEmpty();
      if (parens) {
        sb.append('(');
      }
      sb.append(efVar.getName()).append(": ").append(arg);
      if (parens) {
        sb.append(')');
      }
      if (i + 1 < type.getCtorArgs().size()) {
        sb.append(", ");
      }
    }
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

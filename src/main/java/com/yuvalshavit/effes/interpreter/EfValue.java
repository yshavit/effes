package com.yuvalshavit.effes.interpreter;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Longs;
import com.yuvalshavit.effes.compile.node.BuiltinType;
import com.yuvalshavit.effes.compile.node.EfType;

import java.util.Collections;
import java.util.List;

public abstract class EfValue {
  private final EfType.SimpleType type;
  private final List<EfValue> state;

  public static EfValue of(EfType.SimpleType type, List<EfValue> state) {
    return new StandardValue(type, state);
  }
  
  public static EfValue of(String value) {
    return new StringValue(value);
  }
  
  public static EfValue of(long value) {
    return new LongValue(value);
  }

  private EfValue(EfType.SimpleType type, List<EfValue> state) {
    this.type = Preconditions.checkNotNull(type);
    this.state = ImmutableList.copyOf(state);
  }

  public EfType.SimpleType getType() {
    return type;
  }
  
  public List<EfValue> getState() {
    return state;
  }
  
  public static final class StandardValue extends EfValue {
    
    private StandardValue(EfType.SimpleType type, List<EfValue> state) {
      super(type, state);
      // TODO also check types against expected
    }
    
    @Override
    public String toString() {
      if (getState().isEmpty()) {
        return getType().getName();
      }
      StringBuilder sb = new StringBuilder(getType().getName()).append('(');
      Joiner.on(", ").appendTo(sb, getState());
      sb.append(')');
      return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || o.getClass() != StandardValue.class)
        return false;

      StandardValue efValue = (StandardValue) o;
      return getState().equals(efValue.getState()) && getType().equals(efValue.getType());

    }

    @Override
    public int hashCode() {
      int result = getType().hashCode();
      result = 31 * result + getState().hashCode();
      return result;
    }
  }
  
  private static abstract class BuiltinValue<E extends BuiltinValue<E>> extends EfValue {
    protected abstract boolean equalsComponent(E other);
    @Override
    public abstract int hashCode();
    @Override
    public abstract String toString();
    
    protected BuiltinValue(EfType.SimpleType type) {
      super(type, Collections.emptyList());
    }

    @Override
    public final boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;

      @SuppressWarnings("unchecked")
      E that = (E) o;
      return equalsComponent(that);

    }
  }
  
  public static final class LongValue extends BuiltinValue<LongValue> {
    private final long value;
    
    private LongValue(long value) {
      super(BuiltinType.efTypeFor(value));
      this.value = value;
    }

    @Override
    public int hashCode() {
      return Longs.hashCode(value);
    }

    @Override
    protected boolean equalsComponent(LongValue other) {
      return value == other.value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }
  }
  
  public static final class StringValue extends BuiltinValue<StringValue> {
    private final String value;

    private StringValue(String value) {
      super(BuiltinType.String.getEfType());
      this.value = Preconditions.checkNotNull(value);
    }
    
    public String getString() {
      return value;
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }

    @Override
    protected boolean equalsComponent(StringValue other) {
      return value.equals(other.value);
    }

    @Override
    public String toString() {
      return getString();
    }
  }
}

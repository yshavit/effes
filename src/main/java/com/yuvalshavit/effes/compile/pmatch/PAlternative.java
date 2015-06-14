package com.yuvalshavit.effes.compile.pmatch;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.yuvalshavit.effes.compile.CtorRegistry;
import com.yuvalshavit.effes.compile.node.BuiltinType;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.effes.compile.node.EfVar;
import com.yuvalshavit.util.Equality;

public abstract class PAlternative {
  private PAlternative() {}
  
  @Override public abstract int hashCode();
  @Override public abstract boolean equals(Object other);
  @Override public abstract String toString();
  
  public interface Builder {
    @Nullable
    PAlternative build(CtorRegistry ctors);
  }
  
  public static Builder any(String name) {
    return (c) -> new Any(name);
  }

  public static Builder any() {
    return any("_");
  }
  
  public static Builder simple(EfType.SimpleType type, Builder... args) {
    if (BuiltinType.isBuiltinWithLargeDomain(type)) {
      checkArgument(args.length == 0, "%s doesn't take any arguments", type);
      return (c) -> new Simple(new TypedValue.LargeDomainValue<>(type));
    }
    return (c) -> {
      List<PAlternative> builtArgs = Stream.of(args).map(b -> b.build(c)).collect(Collectors.toList());
      if (builtArgs.stream().anyMatch(Objects::isNull)) {
        return null;
      }
      Simple simple = new Simple(new TypedValue.StandardValue<>(type, builtArgs));
      return simple.validate(c, type.getReification())
        ? simple
        : null;
    };
  }
  
  static class Any extends PAlternative {
    private static final Equality<Any> equality = Equality.forClass(Any.class).with("name", Any::name).exactClassOnly();
    private final String name;

    private Any(String name) {
      this.name = name;
    }

    private String name() {
      return name;
    }

    @Override
    public String toString() {
      return name;
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
  
  static class Simple extends PAlternative {
    private static final Equality<Simple> equality = Equality.forClass(Simple.class).with("value", Simple::value).exactClassOnly();
    private final TypedValue<PAlternative> value;

    private Simple(TypedValue<PAlternative> value) {
      this.value = value;
    }

    public TypedValue<PAlternative> value() {
      return value;
    }

    public boolean validate(CtorRegistry ctors, Function<EfType.GenericType, EfType> reification) {
      return value.handle(
        l -> true,
        s -> {
          List<EfType> expecteds = Lists.transform(ctors.get(value.type(), reification), EfVar::getType);
          List<PAlternative> actuals = s.args();
          if (expecteds.size() != actuals.size()) {
            return false;
          }
          for (int i = 0; i < expecteds.size(); ++i) {
            PAlternative actual = actuals.get(i);
            if (actual instanceof PAlternative.Simple) {
              Simple simple = (Simple) actual;
              EfType expected = expecteds.get(i);
              if (!expected.contains(simple.value.type())) {
                return false;
              }
              if (!simple.validate(ctors, reification)) {
                return false;
              }
            } else {
              assert actual instanceof Any : actual;
            }
          }
          return true;
        });
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
      return value.handle(
        l -> l.type().toString(),
        s -> {
          List<PAlternative> args = s.args();
          if (args.isEmpty()) {
            return s.type().toString();
          } else {
            StringBuilder sb = new StringBuilder(s.type().toString()).append('(');
            return Joiner.on(", ").appendTo(sb, args).append(')').toString();
          }
        });
    }
  }
}

package com.yuvalshavit.effes.compile.pmatch;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.yuvalshavit.effes.compile.node.BuiltinType;
import com.yuvalshavit.effes.compile.node.CtorArg;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.util.EfCollections;
import com.yuvalshavit.util.EfFunctions;
import com.yuvalshavit.util.Lazy;

public abstract class PPossibility {
  
  @Nonnull
  public abstract <R> R dispatch(
    Function<? super Disjunction, ? extends R> onDisjunction,
    Function<? super Simple, ? extends R> onSimple,
    Supplier<? extends R> onNone);


  public void consume(
    Consumer<? super Disjunction> onDisjunction,
    Consumer<? super Simple> onSimple,
    Runnable onNone)
  {
    dispatch(
      EfFunctions.from(onDisjunction),
      EfFunctions.from(onSimple),
      () -> { onNone.run(); return null; }
    );
  }
  
  public abstract String toString(boolean verbose);
  
  public abstract Collection<Simple> components();

  @Override
  public String toString() {
    return toString(false);
  }

  private PPossibility() {}
  
  public static TypedPossibility<? extends EfType> from(EfType type) throws PPossibilityCreationException {
    if (type instanceof EfType.SimpleType) {
      return fromSimple((EfType.SimpleType) type);
    } else if (type instanceof EfType.DisjunctiveType) {
      EfType.DisjunctiveType disjunction = (EfType.DisjunctiveType) type;
      Set<EfType> alternatives = disjunction.getAlternatives();
      List<EfType.SimpleType> simpleAlternatives = new ArrayList<>(alternatives.size());
      for (EfType alternative : alternatives) {
        if (alternative instanceof EfType.SimpleType) {
          simpleAlternatives.add(((EfType.SimpleType) alternative));
        } else {
          throw new PPossibilityCreationException("not a valid type for a case statement: " + alternative);
        }
      }
      List<Simple> possibilities = simpleAlternatives
        .stream()
        .map(PPossibility::fromSimple)
        .collect(Collectors.toList());
      return new PPossibility.Disjunction(possibilities);
    } else {
      throw new PPossibilityCreationException("not a valid type for a case statement: " + type);
    }
  }
  
  public static LazyPossibility lazy(TypedPossibility<? extends EfType> possibility) {
    return new LazyPossibility(Lazy.forced(possibility), possibility.efType());
  }

  private static Simple fromSimple(EfType.SimpleType type) {
    TypedValue<LazyPossibility> value;
    List<String> argNames;
    if (BuiltinType.isBuiltinWithLargeDomain(type)) {
      value = new TypedValue.LargeDomainValue<>(type);
      argNames = Collections.emptyList();
    } else {
      //    List<EfVar> args = argsByType.get(type.getGeneric());
      //    Preconditions.checkArgument(args != null, "unknown type: " + type);
      //    return args.stream().map(v -> v.reify(reification)).collect(Collectors.toList());
      List<CtorArg> ctorVars = type.getArgs(type.getReification());
      List<LazyPossibility> args  = new ArrayList<>(ctorVars.size());
      for (CtorArg ctorVar : ctorVars) {
        args.add(lazy(from(ctorVar.type())));
      }
      value = new TypedValue.StandardValue<>(type, args);
      argNames = ctorVars.stream().map(CtorArg::name).collect(Collectors.toList());
    }
    return new Simple(type, value, argNames);
  }

  public static final PPossibility none = new PPossibility() {

    @Nonnull
    @Override
    public <R> R dispatch(
      Function<? super Disjunction, ? extends R> onDisjunction,
      Function<? super Simple, ? extends R> onSimple,
      Supplier<? extends R> onNone)
    {
      return onNone.get();
    }

    @Override
    public int hashCode() {
      return -2083232260; // rolled a die :)
    }

    @Override
    public boolean equals(Object other) {
      return this == other;
    }

    @Override
    public String toString(boolean verbose) {
      return "∅";
    }

    @Override
    public Collection<Simple> components() {
      return Collections.emptyList();
    }
  };

  public static abstract class TypedPossibility<T extends EfType> extends PPossibility {
    private final T type;

    private TypedPossibility(T type) {
      this.type = type;
    }

    public T efType() {
      return type;
    }
  }

  static class Simple extends TypedPossibility<EfType.SimpleType> {
    private final TypedValue<LazyPossibility> value;
    private final List<String> argNames;

    private Simple(EfType.SimpleType type, TypedValue<LazyPossibility> value, List<String> argNames) {
      super(type);
      this.value = value;
      this.argNames = argNames;
    }

    @Nonnull
    @Override
    public <R> R dispatch(
      Function<? super Disjunction, ? extends R> onDisjunction,
      Function<? super Simple, ? extends R> onSimple,
      Supplier<? extends R> onNone)
    {
      return onSimple.apply(this);
    }

    @Override
    public Collection<Simple> components() {
      return Collections.singletonList(this);
    }

    @VisibleForTesting
    TypedValue<LazyPossibility> typedAndArgs() {
      return value;
    }

    public Simple withArgs(List<LazyPossibility> args) {
      List<EfType> efArgs = Lists.transform(args, LazyPossibility::efType);
      EfType.SimpleType newEfType = efType().withCtorArgs(efArgs);
      return new Simple(newEfType, new TypedValue.StandardValue<>(value.type(), args), argNames);
    }

    @Override
    public String toString(boolean verbose) {
      return value.transform(
        large -> toString(large.type(), verbose),
        std -> {
          List<LazyPossibility> args = std.args();
          if (args.isEmpty()) {
            return toString(std.type(), verbose);
          } else {
            StringBuilder sb = new StringBuilder(toString(std.type(), verbose)).append('(');
            Iterable<? extends String> namedArgs;
            namedArgs = verbose
              ? EfCollections.zipF(argNames, args, (n, a) -> String.format("%s: (%s)", n, a))
              : Lists.transform(args, Functions.toStringFunction());
            return Joiner.on(", ").appendTo(sb, namedArgs).append(')').toString();
          }
        });
    }

    private static String toString(EfType.SimpleType type, boolean verbose) {
      return verbose
        ? type.toString()
        : type.getName();
    }
  }
  
  static class Disjunction extends TypedPossibility<EfType.DisjunctiveType> {
    private final List<PPossibility.Simple> options;

    @Nonnull
    @Override
    public <R> R dispatch(
      Function<? super Disjunction, ? extends R> onDisjunction,
      Function<? super Simple, ? extends R> onSimple,
      Supplier<? extends R> onNone)
    {
      return onDisjunction.apply(this);
    }

    public Disjunction(Collection<Simple> options) {
      super(createDisjunction(options));
      checkArgument(options.size() > 1, options);
      this.options = new ArrayList<>(options);
    }

    private static EfType.DisjunctiveType createDisjunction(Collection<Simple> options) {
      return (EfType.DisjunctiveType) EfType.disjunction(Collections2.transform(options, Simple::efType));
    }

    public List<Simple> options() {
      return options;
    }

    @Override
    public Collection<Simple> components() {
      List<Simple> r = new ArrayList<>(this.options);
      r.sort(Comparator.comparing(Functions.toStringFunction()::apply));
      return Collections.unmodifiableList(r);
    }

    @Override
    public String toString(boolean verbose) {
      List<String> verboseOptions = Lists.transform(options, s -> s.toString(verbose));
      return Joiner.on(" | ").join(verboseOptions);
    }
  }
}

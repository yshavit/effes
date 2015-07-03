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
import com.google.common.collect.Lists;
import com.yuvalshavit.effes.compile.CtorRegistry;
import com.yuvalshavit.effes.compile.node.BuiltinType;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.effes.compile.node.EfVar;
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
  
  public static PPossibility from(EfType type, CtorRegistry ctors) {
    if (type instanceof EfType.SimpleType) {
      return fromSimple((EfType.SimpleType) type, ctors);
    } else if (type instanceof EfType.DisjunctiveType) {
      EfType.DisjunctiveType disjunction = (EfType.DisjunctiveType) type;
      Set<EfType> alternatives = disjunction.getAlternatives();
      List<EfType.SimpleType> simpleAlternatives = new ArrayList<>(alternatives.size());
      for (EfType alternative : alternatives) {
        if (alternative instanceof EfType.SimpleType) {
          simpleAlternatives.add(((EfType.SimpleType) alternative));
        } else {
          // TODO report an error!
          return PPossibility.none;
        }
      }
      List<Simple> possibilities = simpleAlternatives
        .stream()
        .map(t -> fromSimple(t, ctors))
        .collect(Collectors.toList());
      return new PPossibility.Disjunction(possibilities);
    } else {
      // TODO report an error!
      return none;
    }
  }

  private static Simple fromSimple(EfType.SimpleType type, CtorRegistry ctors) {
    TypedValue<Lazy<PPossibility>> value;
    List<String> argNames;
    if (BuiltinType.isBuiltinWithLargeDomain(type)) {
      value = new TypedValue.LargeDomainValue<>(type);
      argNames = Collections.emptyList();
    } else {
      List<EfVar> ctorVars = ctors.get(type, type.getReification());
      List<Lazy<PPossibility>> args  = new ArrayList<>(ctorVars.size());
      for (EfVar ctorVar : ctorVars) {
        args.add(Lazy.lazy(() -> from(ctorVar.getType(), ctors)));
      }
      value = new TypedValue.StandardValue<>(type, args);
      argNames = ctorVars.stream().map(EfVar::getName).collect(Collectors.toList());
    }
    return new Simple(value, argNames);
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

  static class Simple extends PPossibility {
    private final TypedValue<Lazy<PPossibility>> value;
    private final List<String> argNames;

    private Simple(TypedValue<Lazy<PPossibility>> value, List<String> argNames) {
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
    TypedValue<Lazy<PPossibility>> typedAndArgs() {
      return value;
    }

    public Simple withArgs(List<Lazy<PPossibility>> args) {
      return new Simple(new TypedValue.StandardValue<>(value.type(), args), argNames);
    }

    @Override
    public String toString(boolean verbose) {
      return value.transform(
        large -> toString(large.type(), verbose),
        std -> {
          List<Lazy<PPossibility>> args = std.args();
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
  
  static class Disjunction extends PPossibility {
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
      checkArgument(options.size() > 1, options);
      this.options = new ArrayList<>(options);
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

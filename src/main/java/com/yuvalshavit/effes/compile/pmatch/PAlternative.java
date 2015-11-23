package com.yuvalshavit.effes.compile.pmatch;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.yuvalshavit.effes.compile.node.BuiltinType;
import com.yuvalshavit.effes.compile.node.CtorArg;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.util.EfCollections;
import com.yuvalshavit.util.Equality;
import com.yuvalshavit.util.Lazy;

public abstract class PAlternative {

  public static final String NON_CAPTURING_WILD = "_";

  private PAlternative() {}
  
  @Override public abstract int hashCode();
  @Override public abstract boolean equals(Object other);
  @Override public abstract String toString();
  
  public interface Builder {
    @Nonnull
    PAlternative build();
  }
  
  public static Builder any(String name) {
    checkNotNull(name, "name");
    return () -> new Any(name);
  }

  public static Builder any() {
    return any(NON_CAPTURING_WILD);
  }

  public static Builder simple(EfType.SimpleType type, Builder... args) {
    if (BuiltinType.isBuiltinWithLargeDomain(type)) {
      checkArgument(args.length == 0, "%s doesn't take any arguments", type);
      return () -> new Simple(new TypedValue.LargeDomainValue<>(type));
    }
    return () -> {
      List<PAlternative> builtArgs = Stream.of(args).map(Builder::build).collect(Collectors.toList());
      Simple simple = new Simple(new TypedValue.StandardValue<>(type, builtArgs));
      if (!simple.validate(type.getReification())) {
        throw new IllegalArgumentException(String.format("failed validation for %s(%s)", type, Joiner.on(", ").join(args)));
      }
      return simple;
    };
  }

  @Nullable
  private ForcedPossibility subtractFrom(EfType type, PPossibility pPossibility) {
    if (PPossibility.none.equals(pPossibility)) {
      return null;
    }
    StepResult result = subtractFrom(new LazyPossibility(Lazy.forced(pPossibility), type));
    if (result.noneMatched()) {
      return null;
    }
    List<PPossibility.Simple> simples = result.getUnmatched().stream()
      .flatMap(lp -> lp.possibility.get().components().stream())
      .collect(Collectors.toList());
    if (simples.isEmpty()) {
      return new ForcedPossibility(PPossibility.none, EfType.UNKNOWN, result.bindings());
    } else if (simples.size() == 1) {
      PPossibility.Simple only = Iterables.getOnlyElement(simples);
      return new ForcedPossibility(only, only.efType(), result.bindings());
    } else {
      EfType efDisjunction = EfType.disjunction(Lists.transform(simples, PPossibility.TypedPossibility::efType));
      PPossibility.Disjunction possibilityDisjunction = new PPossibility.Disjunction(simples);
      return new ForcedPossibility(possibilityDisjunction, efDisjunction, result.bindings());
    }
  }

  @Nullable
  public ForcedPossibility subtractFrom(PPossibility.TypedPossibility<? extends EfType> possibility) {
    return subtractFrom(possibility.efType(), possibility);
  }
  
  @Nullable
  public ForcedPossibility subtractFrom(ForcedPossibility possibility) {
    return subtractFrom(possibility.efType(), possibility.possibility());
  }
  
  protected abstract StepResult subtractFrom(LazyPossibility pPossibility);
  
  static class Any extends PAlternative {
    private static final Equality<Any> equality = Equality.forClass(Any.class).with("name", Any::name).exactClassOnly();
    private final String name;

    private Any(String name) {
      this.name = checkNotNull(name, "name");
    }

    private String name() {
      return name;
    }

    @Override
    public StepResult subtractFrom(LazyPossibility pPossibility) {
      StepResult r = new StepResult();
      r.addMatched(pPossibility);
      if (!NON_CAPTURING_WILD.equals(name)) {
        r.addBinding(name, pPossibility.efType());
      }
      return r;
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

    @Override
    public StepResult subtractFrom(LazyPossibility pPossibility) {
      return pPossibility.possibility().get().dispatch(
        this::handle,
        s -> handle(pPossibility, s),
        this::handleNone);
    }
    
    private StepResult handle(PPossibility.Disjunction disjunction) {
      StepResult result = new StepResult();
      for (PPossibility.Simple option : disjunction.options()) {
        LazyPossibility lazyPossibility = new LazyPossibility(Lazy.forced(option), option.efType());
        StepResult step = subtractFrom(lazyPossibility);
        result.addUnmatched(step.getUnmatched());
        if (step.anyMatched()) {
          result.addMatched(step.getMatched());
        }
        result.addBindings(step.bindings());
      }
      return result;
    }

    private StepResult handle(LazyPossibility lazyPossibility, PPossibility.Simple simple) {
      TypedValue<LazyPossibility> simpleTypeAndArgs = simple.typedAndArgs();
      if (!EfType.sameRawType(simpleTypeAndArgs.type(), value.type())) {
        StepResult r = new StepResult();
        r.addUnmatched(lazyPossibility);
        return r;
      }
      
      return value.transform(
        largeAlternative -> subtractFromLarge(lazyPossibility),
        standardAlternative -> simpleTypeAndArgs.transform(
          largePossibility -> {
            throw new AssertionError(
              String.format(
                "types should be the same, but alternative is standard while possibility is large: %s != %s",
                standardAlternative,
                largePossibility));
          },
          simplePossibility -> subtractFromStandard(
            value.type(),
            standardAlternative.args(),
            lazyPossibility,
            simplePossibility,
            args -> {
              PPossibility.Simple simpleResult = simple.withArgs(args);
              return new LazyPossibility(Lazy.forced(simpleResult), simpleResult.efType());
            })));
    }
    
    private StepResult subtractFromLarge(LazyPossibility possibility) {
      StepResult r = new StepResult();
      r.addMatched(possibility);
      r.addUnmatched(possibility);
      return r;
    }

    private StepResult subtractFromStandard(
      EfType.SimpleType type,
      List<PAlternative> alternativeArgs,
      LazyPossibility lazyPossibility,
      TypedValue.StandardValue<LazyPossibility> possibility,
      Function<List<LazyPossibility>, LazyPossibility> possibilityMaker)
    {
      List<LazyPossibility> possibilityArgs = possibility.args();
      int nargs = possibilityArgs.size();
      checkArgument(alternativeArgs.size() == nargs, "mismatched args for %s: %s != %s", type, alternativeArgs, possibilityArgs);

      if (nargs == 0) {
        // handle no-arg types specially. The caller already validated that the type is right, so we can just say that it matched
        StepResult result = new StepResult();
        result.addMatched(lazyPossibility);
        return result;
      }

      StepResult result = new StepResult();
      List<StepResult> resultArgs = new ArrayList<>(nargs);
      for (int i = 0; i < nargs; ++i) {
        PAlternative alternativeArg = alternativeArgs.get(i);
        LazyPossibility possibilityArg = possibilityArgs.get(i);
        StepResult argResult = alternativeArg.subtractFrom(possibilityArg);
        if (argResult.noneMatched()) {
          StepResult r = new StepResult(); // Do I want some sort of detail-level reporting to the user?
          r.addUnmatched(lazyPossibility);
          return r;
        }
        result.addBindings(argResult.bindings());
        resultArgs.add(argResult);
      }

      Collection<List<ExplodeArg>> exploded = explode(resultArgs);

      exploded.stream()
        .filter(args -> args.stream().anyMatch(ExplodeArg::notFromMatched))
        .map(args -> Lists.transform(args, ExplodeArg::value))
        .map(possibilityMaker::apply)
        .forEach(result::addUnmatched);
      exploded.stream()
        .filter(args -> args.stream().allMatch(ExplodeArg::fromMatched))
        .map(args -> Lists.transform(args, ExplodeArg::value))
        .map(possibilityMaker::apply)
        .forEach(result::addMatched);
      return result;
    }

    private Collection<List<ExplodeArg>> explode(List<StepResult> stepArgs) {
      Iterator<StepResult> iter = stepArgs.iterator();
      if (!iter.hasNext()) {
        return Collections.emptyList();
      }
      Collection<List<ExplodeArg>> exploded = new ArrayList<>();
      StepResult firstArgs = iter.next();
      exploded.addAll(Collections2.transform(firstArgs.getMatched(), matched -> Collections.singletonList(new ExplodeArg(matched, true))));
      exploded.addAll(Collections2.transform(firstArgs.getUnmatched(), unmatched -> Collections.singletonList(new ExplodeArg(unmatched, false))));
      while (iter.hasNext()) {
        StepResult args = iter.next();
        Collection<List<ExplodeArg>> tmp = new ArrayList<>();
        Set<ExplodeArg> addArgs = new HashSet<>(args.getUnmatched().size() + 1);
        args.getMatched().forEach(matched -> addArgs.add(new ExplodeArg(matched, true)));
        args.getUnmatched().forEach(unmatched -> addArgs.add(new ExplodeArg(unmatched, false)));
        if (addArgs.isEmpty()) throw new UnsupportedOperationException();
        exploded.forEach(prevArgs -> {
          args.getMatched().forEach(matched -> tmp.add(EfCollections.concatList(prevArgs, new ExplodeArg(matched, true))));
          args.getUnmatched().forEach(unmatched -> tmp.add(EfCollections.concatList(prevArgs, new ExplodeArg(unmatched, false))));
        });
        exploded = tmp;
      }
      return exploded;
    }

    private StepResult handleNone() {
      return new StepResult();
    }

    public boolean validate(Function<EfType.GenericType, EfType> reification) {
      return value.transform(
        l -> true,
        s -> {
          //    List<EfVar> args = argsByType.get(type.getGeneric());
          //    Preconditions.checkArgument(args != null, "unknown type: " + type);
          //    return args.stream().map(v -> v.reify(reification)).collect(Collectors.toList());
          List<EfType> expecteds = Lists.transform(value.type().reify(reification).getArgs(), CtorArg::type);
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
              if (!simple.validate(simple.value().type().getReification())) {
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
      return value.transform(
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

  private static class ExplodeArg {
    private final LazyPossibility possibility;
    private final boolean fromMatched;

    public ExplodeArg(LazyPossibility possibility, boolean fromMatched) {
      this.possibility = possibility;
      this.fromMatched = fromMatched;
    }

    public LazyPossibility value() {
      return possibility;
    }
    
    public boolean fromMatched() {
      return fromMatched;
    }

    public boolean notFromMatched() {
      return ! fromMatched;
    }

    @Override
    public String toString() {
      return String.format("%smatched %s",
        (fromMatched
          ? ""
          : "un"),
        possibility);
    }
  }
}

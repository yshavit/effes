package com.yuvalshavit.effes.compile.pmatch;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.yuvalshavit.effes.compile.CtorRegistry;
import com.yuvalshavit.effes.compile.node.BuiltinType;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.effes.compile.node.EfVar;
import com.yuvalshavit.util.EfCollections;
import com.yuvalshavit.util.Equality;
import com.yuvalshavit.util.Lazy;

public abstract class PAlternative {
  private PAlternative() {}
  
  @Override public abstract int hashCode();
  @Override public abstract boolean equals(Object other);
  @Override public abstract String toString();
  
  public interface Builder {
    @Nonnull
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
      Simple simple = new Simple(new TypedValue.StandardValue<>(type, builtArgs));
      if (!simple.validate(c, type.getReification())) {
        throw new IllegalArgumentException(String.format("failed validation for %s(%s)", type, Joiner.on(", ").join(args)));
      }
      return simple;
    };
  }
  
  public PPossibility subtractFrom(PPossibility pPossibility) {
    StepResult result = subtractFrom(Lazy.forced(pPossibility));
    result.getMatched(); // confirm that there's a match
    List<PPossibility.Simple> simples = result.getUnmatched().stream()
      .map(Lazy::get)
      .flatMap(p -> p.components().stream())
      .collect(Collectors.toList());
    if (simples.isEmpty()) {
      return PPossibility.none;
    } else if (simples.size() == 1) {
      return Iterables.getOnlyElement(simples);
    } else {
      return new PPossibility.Disjunction(simples);
    }
  }
  
  protected abstract StepResult subtractFrom(Lazy<PPossibility> pPossibility);
  
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
    public StepResult subtractFrom(Lazy<PPossibility> pPossibility) {
      StepResult r = new StepResult();
      r.addMatched(pPossibility);
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
  
  static final Deque<String> messages = new ArrayDeque<>(); // TODO REMOVE! Only meant for debugging!
  
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
    public StepResult subtractFrom(Lazy<PPossibility> pPossibility) {
      messages.push(String.format("subtracting %s from %s", this, pPossibility.get()));
      try {
        return pPossibility.get().dispatch(
          this::handle,
          s -> handle(pPossibility, s),
          this::handleNone);
      } finally {
        messages.pop();
      }
    }
    
    private StepResult handle(PPossibility.Disjunction disjunction) {
      StepResult result = new StepResult();
      for (PPossibility.Simple option : disjunction.options()) {
        Lazy<PPossibility> lazyOption = Lazy.forced(option);
        StepResult step = subtractFrom(lazyOption);
        result.addUnmatched(step.getUnmatched());
        if (step.anyMatched()) {
          result.addMatched(step.getMatched());
        }
      }
      return result;
    }

    private StepResult handle(Lazy<PPossibility> lazyPossibility, PPossibility.Simple simple) {
      TypedValue<Lazy<PPossibility>> simpleTypeAndArgs = simple.typedAndArgs();
      if (!simpleTypeAndArgs.type().equals(value.type())) {
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
          simplePossibility -> subtractFromStandard(value.type(), standardAlternative.args(), lazyPossibility, simplePossibility, simple::withArgs)));
    }
    
    private StepResult subtractFromLarge(Lazy<PPossibility> possibility) {
      StepResult r = new StepResult();
      r.addMatched(possibility);
      r.addUnmatched(possibility);
      return r;
    }

    private StepResult subtractFromStandard(
      EfType.SimpleType type,
      List<PAlternative> alternativeArgs,
      Lazy<PPossibility> lazyPossibility,
      TypedValue.StandardValue<Lazy<PPossibility>> possibility,
      Function<List<Lazy<PPossibility>>, PPossibility> possibilityMaker)
    {
      List<Lazy<PPossibility>> possibilityArgs = possibility.args();
      int nargs = possibilityArgs.size();
      checkArgument(alternativeArgs.size() == nargs, "mismatched args for %s: %s != %s", type, alternativeArgs, possibilityArgs);

      if (nargs == 0) {
        // handle no-arg types specially. The caller already validated that the type is right, so we can just say that it matched
        StepResult result = new StepResult();
        result.addMatched(lazyPossibility);
        return result;
      }
      
      List<StepResult> resultArgs = new ArrayList<>(nargs);
      for (int i = 0; i < nargs; ++i) {
        PAlternative alternativeArg = alternativeArgs.get(i);
        Lazy<PPossibility> possibilityArg = possibilityArgs.get(i);
        StepResult argResult = alternativeArg.subtractFrom(possibilityArg);
        if (argResult.noneMatched()) {
          StepResult r = new StepResult(); // Do I want some sort of detail-level reporting to the user?
          r.addUnmatched(lazyPossibility);
          return r;
        }
        resultArgs.add(argResult);
      }

      Collection<List<ExplodeArg>> exploded = explode(resultArgs);

      StepResult result = new StepResult();
      exploded.stream()
        .filter(args -> args.stream().anyMatch(ExplodeArg::notFromMatched))
        .map(args -> Lists.transform(args, ExplodeArg::value))
        .map(combo -> Lazy.forced(possibilityMaker.apply(combo)))
        .forEach(result::addUnmatched);
      exploded.stream()
        .filter(args -> args.stream().allMatch(ExplodeArg::fromMatched))
        .map(args -> Lists.transform(args, ExplodeArg::value))
        .map(combo -> Lazy.forced(possibilityMaker.apply(combo)))
        .forEach(result::addMatched);
      
      /*
      
      Collection<List<Lazy<PPossibility>>> unmatchedArgCombos = exploded.stream()
        .filter(args -> args.stream().anyMatch(ExplodeArg::notFromMatched))
        .map(args -> Lists.transform(args, ExplodeArg::value))
        .collect(Collectors.toList());
      return Collections2.transform(unmatchedArgCombos, args -> Lazy.forced(typeMaker.apply(args)));
       */

      //      result.addMatched(fromMatched(possibilityMaker, resultArgs));
      //      result.addUnmatched(fromUnmatched(possibilityMaker, resultArgs));
      return result;
    }

//    private Collection<Lazy<PPossibility>> fromMatched(Function<List<Lazy<PPossibility>>, PPossibility> typeMaker, List<StepResult> stepArgs) {
//      return Lazy.forced(typeMaker.apply(Lists.transform(stepArgs, StepResult::getMatched)));
//    }

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

    public boolean validate(CtorRegistry ctors, Function<EfType.GenericType, EfType> reification) {
      return value.transform(
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
              if (!simple.validate(ctors, simple.value().type().getReification())) {
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
            return s.type().getName();
          } else {
            StringBuilder sb = new StringBuilder(s.type().getName()).append('(');
            return Joiner.on(", ").appendTo(sb, args).append(')').toString();
          }
        });
    }
  }

  private static class ExplodeArg {
    private final Lazy<PPossibility> possibility;
    private final boolean fromMatched;

    public ExplodeArg(Lazy<PPossibility> possibility, boolean fromMatched) {
      this.possibility = possibility;
      this.fromMatched = fromMatched;
    }

    public Lazy<PPossibility> value() {
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

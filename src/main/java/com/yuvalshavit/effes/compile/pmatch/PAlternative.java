package com.yuvalshavit.effes.compile.pmatch;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

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
  
  public PPossibility subtractFrom(PPossibility pPossibility) {
    StepResult2 result = subtractFrom(Lazy.forced(pPossibility));
    result.getOnlyMatched(); // confirm that there's a match
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
  
  protected abstract StepResult2 subtractFrom(Lazy<PPossibility> pPossibility);
  
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
    public StepResult2 subtractFrom(Lazy<PPossibility> pPossibility) {
      StepResult2 r = new StepResult2();
      r.setMatched(pPossibility);
//      r.addUnmatched(pPossibility);
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
    public StepResult2 subtractFrom(Lazy<PPossibility> pPossibility) {
      return pPossibility.get().dispatch(
        this::handle, 
        s -> handle(pPossibility, s), 
        this::handleNone);
    }
    
    private StepResult2 handle(PPossibility.Disjunction disjunction) {
      StepResult2 result = new StepResult2();
      Iterator<PPossibility.Simple> iterator = disjunction.options().iterator();
      while (iterator.hasNext()) {
        PPossibility.Simple option = iterator.next();
        Lazy<PPossibility> lazyOption = Lazy.forced(option); // TODO make disjunction lazy
        StepResult2 step = subtractFrom(lazyOption);
        result.addUnmatched(step.getUnmatched());
        if (step.anyMatched()) {
          result.setMatched(lazyOption);
          break;
        }
      }
      // If one of the early options matched, we need to add the rest. Assume they're unmatched.
      iterator.forEachRemaining(step -> result.addUnmatched(Lazy.forced(step)));
      return result;
    }

    private StepResult2 handle(Lazy<PPossibility> lazyPossibility, PPossibility.Simple simple) {
      TypedValue<Lazy<PPossibility>> simpleTypeAndArgs = simple.typedAndArgs();
      if (!simpleTypeAndArgs.type().equals(value.type())) {
        StepResult2 r = new StepResult2();
        r.addUnmatched(lazyPossibility);
        return r;
      }
      
      return value.transform(
        largeAlternative -> subtractFromLarge(),
        simpleAlternative -> simpleTypeAndArgs.transform(
          largePossibility -> subtractFromLarge(),
          simplePossibility -> subtractFromStandard(value.type(), simpleAlternative.args(), lazyPossibility, simplePossibility, simple::withArgs)));
    }
    
    private StepResult2 subtractFromLarge() {
      throw new UnsupportedOperationException(); // TODO
    }

    private StepResult2 subtractFromStandard(
      EfType.SimpleType type,
      List<PAlternative> alternativeArgs,
      Lazy<PPossibility> lazyPossibility,
      TypedValue.StandardValue<Lazy<PPossibility>> possibility,
      Function<List<Lazy<PPossibility>>, PPossibility> possibilityMaker)
    {
      List<Lazy<PPossibility>> possibilityArgs = possibility.args();
      int nargs = possibilityArgs.size();
      checkArgument(alternativeArgs.size() == nargs, "mismatched args for %s: %s != %s", type, alternativeArgs, possibilityArgs);
      List<StepResult2> resultArgs = new ArrayList<>(nargs);
      for (int i = 0; i < nargs; ++i) {
        PAlternative alternativeArg = alternativeArgs.get(i);
        Lazy<PPossibility> possibilityArg = possibilityArgs.get(i);
        StepResult2 argResult = alternativeArg.subtractFrom(possibilityArg);
        if (argResult.noneMatched()) {
          StepResult2 r = new StepResult2();
          r.addUnmatched(lazyPossibility);
          return r;
        }
        resultArgs.add(argResult);
      }
      StepResult2 result = new StepResult2();
      result.setMatched(fromMatched(possibilityMaker, resultArgs));
      result.addUnmatched(fromUnmatched(possibilityMaker, resultArgs));
      return result;
    }

    private Lazy<PPossibility> fromMatched(Function<List<Lazy<PPossibility>>, PPossibility> typeMaker, List<StepResult2> stepArgs) {
      return Lazy.forced(typeMaker.apply(Lists.transform(stepArgs, StepResult2::getOnlyMatched)));
    }

    private Collection<Lazy<PPossibility>> fromUnmatched(Function<List<Lazy<PPossibility>>, PPossibility> typeMaker, List<StepResult2> stepArgs) {
      Iterator<StepResult2> iter = stepArgs.iterator();
      if (!iter.hasNext()) {
        return Collections.emptyList();
      }
      Collection<List<ExplodeArg>> exploded = new ArrayList<>();
      StepResult2 firstArgs = iter.next();
      exploded.add(Collections.singletonList(new ExplodeArg(firstArgs.getOnlyMatched(), true)));
      exploded.addAll(Collections2.transform(firstArgs.getUnmatched(), unmatched -> Collections.singletonList(new ExplodeArg(unmatched, false))));
      while (iter.hasNext()) {
        StepResult2 args = iter.next();
        Collection<List<ExplodeArg>> tmp = new ArrayList<>();
        exploded.forEach(prevArgs -> {
          tmp.add(EfCollections.concatList(prevArgs, new ExplodeArg(args.getOnlyMatched(), true)));
          args.getUnmatched().forEach(unmatched -> tmp.add(EfCollections.concatList(prevArgs, new ExplodeArg(unmatched, false))));
        });
        exploded = tmp;
      }
      Collection<List<Lazy<PPossibility>>> unmatchedArgCombos = exploded.stream()
        .filter(args -> args.stream().anyMatch(ExplodeArg::notFromMatched))
        .map(args -> Lists.transform(args, ExplodeArg::value))
        .collect(Collectors.toList());
      return Collections2.transform(unmatchedArgCombos, args -> Lazy.forced(typeMaker.apply(args)));
    }

    private StepResult2 handleNone() {
      return new StepResult2();
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

    public boolean notFromMatched() {
      return ! fromMatched;
    }

    @Override
    public String toString() {
      return String.format("%smatched %s", (fromMatched ? "" : "un"), possibility);
    }
  }
}

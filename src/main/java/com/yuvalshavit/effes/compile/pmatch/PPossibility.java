package com.yuvalshavit.effes.compile.pmatch;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.yuvalshavit.effes.compile.CtorRegistry;
import com.yuvalshavit.effes.compile.node.BuiltinType;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.effes.compile.node.EfVar;
import com.yuvalshavit.util.EfCollections;
import com.yuvalshavit.util.Lazy;

public abstract class PPossibility {
  
  @Nullable
  public final PPossibility minus(PAlternative alternative) {
    checkNotNull(alternative);
    if (alternative instanceof PAlternative.Any) {
      return none;
    }
    StepResult result = subtractionStep(alternative);
    if (result.noneMatched()) {
      return null;
    }
    result.getOnlyMatched(); // confirm that there's only the one
    Collection<PPossibility> remaining = result.getUnmatched(); // TODO make getUnmatched just return the forced type?
    List<Simple> remainingSimples = remaining.stream()
      .map(PPossibility::components)
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
    if (remainingSimples.isEmpty()) {
      return none;
    } else if (remainingSimples.size() == 1) {
      return Iterables.getOnlyElement(remaining);
    } else {
      return new PPossibility.Disjunction(remainingSimples);
    }
  }
  
  @Nonnull
  protected abstract StepResult subtractionStep(PAlternative alternative);
  
  public abstract String toString(boolean verbose);
  
  public abstract Collection<Simple> components();

  @Override
  public String toString() {
    return toString(false);
  }
  
  protected final StepResult handleAny() {
    StepResult r = new StepResult();
    r.setMatched(this);
    r.addUnmatched(this);
    return r;
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

    @Override
    @Nonnull
    protected StepResult subtractionStep(PAlternative alternative) {
      return new StepResult();
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

    @Override
    public Collection<Simple> components() {
      return Collections.singletonList(this);
    }

    @Nonnull
    @Override
    protected StepResult subtractionStep(PAlternative alternative) {
      if (alternative instanceof PAlternative.Any) {
        return handleAny();
      }
      PAlternative.Simple simpleAlternative = (PAlternative.Simple) alternative;
      TypedValue<PAlternative> simpleValue = simpleAlternative.value();
      TypedValue<Lazy<PPossibility>> forcedPossible = this.value;
      if (!forcedPossible.type().equals(simpleValue.type())) {
        StepResult r = new StepResult();
        r.addUnmatched(this);
        return r;
      }
      return forcedPossible.transform(
        p -> large(),
        p -> simpleValue.transform(
          s -> large(), // ditto re decorating the result
          a -> doSubtract(p, a)
        )
      );
    }

    @VisibleForTesting
    TypedValue<Lazy<PPossibility>> typedAndArgs() {
      return value;
    }

    private StepResult doSubtract(TypedValue.StandardValue<Lazy<PPossibility>> possibility, TypedValue.StandardValue<PAlternative> alternative) {
      // given Answer = True | False | Error(reason: Unknown | String)
      // t : Cons(head: Answer, tail: List[Answer])
      //   : Cons(head: True | False | Error(reason: Unknown | String), tail: List[Answer])
      List<Lazy<PPossibility>> possibleArgs = possibility.args();
      List<PAlternative> alternativeArgs = alternative.args();
      int nArgs = possibleArgs.size();
      assert nArgs == alternativeArgs.size() : String.format("different sizes: %s <~> %s", possibleArgs, alternativeArgs);
      
      List<StepResult> resultArgs = new ArrayList<>(nArgs);
      for (int argIdxMutable = 0; argIdxMutable < nArgs; ++argIdxMutable) {
        PPossibility possibleArg = possibleArgs.get(argIdxMutable).get();
        PAlternative alternativeArg = alternativeArgs.get(argIdxMutable);
        StepResult argStep = possibleArg.subtractionStep(alternativeArg);
        if (argStep.noneMatched()) {
          StepResult r = new StepResult();
          r.addUnmatched(this);
          return r;
        }
        resultArgs.add(argStep);
      }
      StepResult result = new StepResult();
      result.setMatched(fromMatchedArgs(resultArgs));
      result.addUnmatched(fromUnmatchedArgs(resultArgs));
      return result;
    }

    private Collection<PPossibility> fromUnmatchedArgs(List<StepResult> resultArgs) {
      Collection<List<PPossibility>> expoded = new ArrayList<>();
      Iterator<StepResult> iter = resultArgs.iterator();
      if (!iter.hasNext()) {
        return Collections.emptyList();
      }
      expoded.addAll(Collections2.transform(iter.next().getUnmatched(), Collections::singletonList));
      while (iter.hasNext()) {
        StepResult arg = iter.next();
        Collection<List<PPossibility>> tmp = new ArrayList<>();
        for (List<PPossibility> prevArgs : expoded) {
          for (PPossibility newArg : arg.getUnmatched()) {
            tmp.add(Lists.newArrayList(Iterables.concat(prevArgs, Collections.singleton(newArg))));
          }
        }
        expoded = tmp;
      }
      return Collections2.transform(expoded, this::createSimilarPossibility);
    }

    private PPossibility fromMatchedArgs(List<StepResult> resultArgs) {
      List<PPossibility> args = Lists.transform(resultArgs, StepResult::getOnlyMatched);
      return createSimilarPossibility(args);
    }

    private Simple createSimilarPossibility(List<PPossibility> args) {
      TypedValue<Lazy<PPossibility>> resultValue = new TypedValue.StandardValue<>(value.type(), Lists.transform(args, Lazy::forced));
      return new Simple(resultValue, argNames);
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

    private StepResult large() {
      // eventually it'd be nice to decorate this with something like "not value.value()"
      return handleAny();
    }

    private static String toString(EfType.SimpleType type, boolean verbose) {
      return verbose
        ? type.toString()
        : type.getName();
    }
  }
  
  static class Disjunction extends PPossibility {
    private final List<PPossibility.Simple> options;

    private Disjunction(Collection<Simple> options) {
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

    @Nonnull
    @Override
    protected StepResult subtractionStep(PAlternative alternative) {
      if (alternative instanceof PAlternative.Any) {
        return handleAny();
      }
        
      Iterator<Simple> iterator = options.iterator();
      StepResult result = new StepResult();
      while (iterator.hasNext()) {
        Simple lazyOption = iterator.next();
        StepResult step = lazyOption.subtractionStep(alternative);
        result.addUnmatched(step.getUnmatched());
        if (step.anyMatched()) {
          result.setMatched(step.getOnlyMatched());
          break;
        }
      }
      // If one of the early options matched, we need to add the rest. Assume they're unmatched.
      iterator.forEachRemaining(result::addUnmatched);
      return result;
    }

    @Override
    public String toString(boolean verbose) {
      List<String> verboseOptions = Lists.transform(options, s -> s.toString(verbose));
      return Joiner.on(" | ").join(verboseOptions);
    }
  }
}

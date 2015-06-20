package com.yuvalshavit.effes.compile.pmatch;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
    Collection<Lazy<PPossibility.Simple>> remaining = result.getUnmatched();
    if (remaining.isEmpty()) {
      return none;
    } else if (remaining.size() == 1) {
      return Iterables.getOnlyElement(remaining).get();
    } else {
      return new PPossibility.Disjunction(remaining);
    }
  }
  
  @Nonnull
  protected abstract StepResult subtractionStep(PAlternative alternative);
  
  public abstract String toString(boolean verbose);

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
      List<Lazy<Simple>> possibilities = simpleAlternatives
        .stream()
        .map(t -> Lazy.lazy(() -> fromSimple(t, ctors)))
        .collect(Collectors.toList());
      return new PPossibility.Disjunction(possibilities);
    } else {
      // TODO report an error!
      return none;
    }
  }

  private static Simple fromSimple(EfType.SimpleType type, CtorRegistry ctors) {
    Lazy<TypedValue<PPossibility>> value;
    List<String> argNames;
    if (BuiltinType.isBuiltinWithLargeDomain(type)) {
      value = Lazy.forced(new TypedValue.LargeDomainValue<>(type));
      argNames = Collections.emptyList();
    } else {
      List<EfVar> ctorVars = ctors.get(type, type.getReification());
      value = Lazy.lazy(
        () -> {
          List<PPossibility> args  = new ArrayList<>(ctorVars.size());
          for (EfVar ctorVar : ctorVars) {
            args.add(from(ctorVar.getType(), ctors));
          }
          return new TypedValue.StandardValue<>(type, args);
        });
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
  };

  static class Simple extends PPossibility {
    private final Lazy<TypedValue<PPossibility>> value;
    private final List<String> argNames;

    private Simple(Lazy<TypedValue<PPossibility>> value, List<String> argNames) {
      this.value = value;
      this.argNames = argNames;
    }

    @Nonnull
    @Override
    protected StepResult subtractionStep(PAlternative alternative) {
      if (alternative instanceof PAlternative.Any) {
        return matchedAndUnmatched();
      }
      PAlternative.Simple simpleAlternative = (PAlternative.Simple) alternative;
      TypedValue<PAlternative> simpleValue = simpleAlternative.value();
      TypedValue<PPossibility> forcedPossible = this.value.get();
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

    private StepResult matchedAndUnmatched() {
      StepResult r = new StepResult();
      r.setMatched(this);
      r.addUnmatched(this);
      return r;
    }

    @VisibleForTesting
    Lazy<TypedValue<PPossibility>> typedAndArgs() {
      return value;
    }

    private StepResult doSubtract(TypedValue.StandardValue<PPossibility> possibility, TypedValue.StandardValue<PAlternative> alternative) {
      // given Answer = True | False | Error(reason: Unknown | String)
      // t : Cons(head: Answer, tail: List[Answer])
      //   : Cons(head: True | False | Error(reason: Unknown | String), tail: List[Answer])
      List<PPossibility> possibleArgs = possibility.args();
      List<PAlternative> alternativeArgs = alternative.args();
      int nArgs = possibleArgs.size();
      assert nArgs == alternativeArgs.size() : String.format("different sizes: %s <~> %s", possibleArgs, alternativeArgs);
      
      List<StepResult> resultArgs = new ArrayList<>(nArgs);
      for (int argIdxMutable = 0; argIdxMutable < nArgs; ++argIdxMutable) {
        PPossibility possibleArg = possibleArgs.get(argIdxMutable);
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

    private Collection<Lazy<PPossibility.Simple>> fromUnmatchedArgs(List<StepResult> resultArgs) {
      Collection<List<PPossibility>> expoded = new ArrayList<>();
      Iterator<StepResult> iter = resultArgs.iterator();
      if (!iter.hasNext()) {
        return Collections.emptyList();
      }
      for (Lazy<Simple> simpleLazy : iter.next().getUnmatched()) {
        expoded.add(Collections.singletonList(simpleLazy.get()));
      }
      while (iter.hasNext()) {
        StepResult arg = iter.next();
        Collection<List<PPossibility>> tmp = new ArrayList<>();
        for (List<PPossibility> prevArgs : expoded) {
          for (Lazy<Simple> newArg : arg.getUnmatched()) {
            tmp.add(Lists.newArrayList(Iterables.concat(prevArgs, Collections.<PPossibility>singleton(newArg.get()))));
          }
        }
        expoded = tmp;
      }
      return Collections2.transform(expoded, args -> Lazy.forced(createSimilarPossibility(args)));
    }

    private PPossibility fromMatchedArgs(List<StepResult> resultArgs) {
      List<PPossibility> args = Lists.transform(resultArgs, StepResult::getOnlyMatched);
      return createSimilarPossibility(args);
    }

    private Simple createSimilarPossibility(List<PPossibility> args) {
      TypedValue<PPossibility> resultValue = new TypedValue.StandardValue<>(value.get().type(), args);
      return new Simple(Lazy.forced(resultValue), argNames);
    }

    @Override
    public String toString(boolean verbose) {
      if (value.isUnforced()) {
        return Lazy.UNFORCED_DESC;
      }
      return value.get().transform(
        large -> toString(large.type(), verbose),
        std -> {
          List<PPossibility> args = std.args();
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
      return matchedAndUnmatched();
    }

    private static String toString(EfType.SimpleType type, boolean verbose) {
      return verbose
        ? type.toString()
        : type.getName();
    }
  }
  
  static class Disjunction extends PPossibility {
    private final List<Lazy<PPossibility.Simple>> options;

    private Disjunction(Collection<Lazy<Simple>> options) {
      this.options = new ArrayList<>(options);
    }

    public List<Lazy<Simple>> options() {
      return options;
    }

    @Nonnull
    @Override
    protected StepResult subtractionStep(PAlternative alternative) {
      Iterator<Lazy<Simple>> iterator = options.iterator();
      StepResult result = new StepResult();
      while (iterator.hasNext()) {
        Lazy<Simple> lazyOption = iterator.next();
        StepResult step = lazyOption.get().subtractionStep(alternative);
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
      List<Lazy<String>> verboseOptions = Lists.transform(options, o -> o.transform(s -> s.toString(verbose)));
      return Joiner.on(" | ").join(verboseOptions);
    }
  }
}

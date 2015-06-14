package com.yuvalshavit.effes.compile.pmatch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.yuvalshavit.effes.compile.CtorRegistry;
import com.yuvalshavit.effes.compile.node.BuiltinType;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.effes.compile.node.EfVar;
import com.yuvalshavit.util.EfCollections;
import com.yuvalshavit.util.Lazy;

public abstract class PPossibility {
  
  @Nullable public abstract PPossibility minus(PAlternative alternative);
  public abstract String toString(boolean verbose);

  @Override
  public String toString() {
    return toString(true);
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
    @Nullable
    public PPossibility minus(PAlternative alternative) {
      return null;
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

  static class Simple extends NonEmpty {
    private final Lazy<TypedValue<PPossibility>> value;
    private final List<String> argNames;

    private Simple(Lazy<TypedValue<PPossibility>> value, List<String> argNames) {
      this.value = value;
      this.argNames = argNames;
    }

    @Nullable
    @Override
    protected PPossibility minusAlternative(PAlternative.Simple simpleAlternative) {
      TypedValue<PAlternative> simpleValue = simpleAlternative.value();
      TypedValue<PPossibility> forcedPossible = this.value.get();
      if (!forcedPossible.type().equals(simpleValue.type())) {
        return null;
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
    Lazy<TypedValue<PPossibility>> typedAndArgs() {
      return value;
    }

    private PPossibility doSubtract(TypedValue.StandardValue<PPossibility> possibility, TypedValue.StandardValue<PAlternative> alternative) {
      // given Answer = True | False | Error(reason: Unknown | String)
      // t : Cons(head: Answer, tail: List[Answer])
      //   : Cons(head: True | False | Error(reason: Unknown | String), tail: List[Answer])
      List<PPossibility> possibleArgs = possibility.args();
      List<PAlternative> alternativeArgs = alternative.args();
      int nArgs = possibleArgs.size();
      assert nArgs == alternativeArgs.size() : String.format("different sizes: %s <~> %s", possibleArgs, alternativeArgs);
      
      List<PPossibility> resultArgs = new ArrayList<>(nArgs);
      for (int argIdxMutable = 0; argIdxMutable < nArgs; ++argIdxMutable) {
        PPossibility possibleArg = possibleArgs.get(argIdxMutable);
        PAlternative alternativeArg = alternativeArgs.get(argIdxMutable);
        PPossibility resultArg = null;
        if (possibleArg instanceof Disjunction) {
          // e.g. head: True | False | Error(reason: Unknown | String)
          Disjunction possibleArgDisjunction = (Disjunction) possibleArg;
          resultArg = possibleArgDisjunction.minus(alternativeArg);
        } else if (possibleArg instanceof Simple) {
          // e.g. just True, or just Error(..)
          resultArg = possibleArg.minus(alternativeArg);
        }
        resultArgs.add(resultArg);
      }
      
      if (resultArgs.stream().allMatch(none::equals)) {
        return none;
      }
      if (resultArgs.stream().anyMatch(Objects::isNull)) {
        return null;
      }
      return new Simple(Lazy.forced(possibility.with(resultArgs)), argNames);
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

    private PPossibility large() {
      return this; // eventually it'd be nice to decorate this with something like "not value.value()"
    }

    private static String toString(EfType.SimpleType type, boolean verbose) {
      return verbose
        ? type.toString()
        : type.getName();
    }
  }
  
  static class Disjunction extends NonEmpty {
    private final List<Lazy<PPossibility.Simple>> options;

    private Disjunction(Collection<Lazy<Simple>> options) {
      this.options = new ArrayList<>(options);
    }

    public List<Lazy<Simple>> options() {
      return options;
    }

    @Nullable
    @Override
    protected PPossibility minusAlternative(PAlternative.Simple simpleAlternative) {
      return subtract(simpleAlternative);
    }

    @Override
    public String toString(boolean verbose) {
      List<Lazy<String>> verboseOptions = Lists.transform(options, o -> o.transform(s -> s.toString(verbose)));
      return Joiner.on(" | ").join(verboseOptions);
    }

    PPossibility subtract(PAlternative alternative) {
      // e.g. disjuction is [ True, False, Error(...) ]
      List<Lazy<Simple>> possibleArgOptions = options();
      for (int optionIdx = 0; optionIdx < possibleArgOptions.size(); ++optionIdx) {
        // e.g. True, or Error(...)
        Simple possibleArgOption = possibleArgOptions.get(optionIdx).get();
        PPossibility matchedArg = possibleArgOption.minus(alternative);
        if (matchedArg != null) { assert  matchedArg == none : matchedArg; // TODO ??
          // e.g. the alternative was True, or Error(_) or something else that matched
          List<Lazy<Simple>> remainingOptions = new ArrayList<>(possibleArgOptions);
          remainingOptions.remove(optionIdx);
          return createFrom(matchedArg, remainingOptions);
        }
      }
      // e.g. the alternative was SomethingElse (not True, False or Error), or it was
      // Error but not in a way that matches our Error(...)
      return null;
    }

    private static PPossibility createFrom(PPossibility first, List<Lazy<Simple>> remaining) {
      if (remaining.isEmpty()) {
        return first;
      }
      List<Lazy<Simple>> alternatives = new ArrayList<>(remaining.size() + 1);
      if (first instanceof Simple) {
        Simple simple = (Simple) first;
        alternatives.add(Lazy.forced(simple));
      } else if (first instanceof Disjunction) {
        Disjunction disjunction = (Disjunction) first;
        alternatives.addAll(disjunction.options());
      } else if (!none.equals(first)) {
        throw new AssertionError("unknown possibility type: " + first);
      }
      alternatives.addAll(remaining);
      if (alternatives.isEmpty()) {
        return none;
      } else if (alternatives.size() == 1) {
        return alternatives.get(0).get();
      } else {
        return new Disjunction(alternatives);
      }
    }
  }

  private static abstract class NonEmpty extends PPossibility {
    @Nullable
    protected abstract PPossibility minusAlternative(PAlternative.Simple simpleAlternative);

    @Nullable
    @Override
    public final PPossibility minus(PAlternative alternative) {
      if (alternative instanceof PAlternative.Any) {
        return none;
      } else {
        PAlternative.Simple simple = (PAlternative.Simple) alternative;
        return minusAlternative(simple);
      }
    }
  }
}

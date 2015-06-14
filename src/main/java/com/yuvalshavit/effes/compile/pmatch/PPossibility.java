package com.yuvalshavit.effes.compile.pmatch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.common.base.Joiner;
import com.yuvalshavit.effes.compile.CtorRegistry;
import com.yuvalshavit.effes.compile.node.BuiltinType;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.effes.compile.node.EfVar;
import com.yuvalshavit.util.EfCollections;
import com.yuvalshavit.util.Lazy;

public abstract class PPossibility {
  
  @Nullable public abstract PPossibility minus(PAlternative alternative);
  
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
        .map(t -> Lazy.from(() -> fromSimple(t, ctors)))
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
      List<EfVar> ctorVars = ctors.get(type, EfType.KEEP_GENERIC);
      List<Lazy<PPossibility>> args = new ArrayList<>(ctorVars.size());
      for (EfVar ctorVar : ctorVars) {
        args.add(Lazy.from(() -> from(ctorVar.getType(), ctors)));
      }
      value = new TypedValue.StandardValue<>(type, args);
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
    public String toString() {
      return "∅";
    }
  };

  public static class Simple extends NonEmpty {
    private final TypedValue<Lazy<PPossibility>> value;
    private final List<String> argNames;

    public Simple(TypedValue<Lazy<PPossibility>> value, List<String> argNames) {
      this.value = value;
      this.argNames = argNames;
    }
    
    PPossibility force(int i) {
      return value.handle(l -> null, s -> s.args().get(i).get());
    }

    @Nullable
    @Override
    protected PPossibility minusAlternative(PAlternative.Simple simpleAlternative) {
      TypedValue<PAlternative> simpleValue = simpleAlternative.value();
      if (!this.value.type().equals(simpleValue.type())) {
        return null;
      }
      return this.value.handle(
        p -> large(),
        p -> simpleValue.handle(
          s -> large(), // ditto re decorating the result
          a -> doSubtract(p, a)
        )
      );
    }

    private PPossibility doSubtract(TypedValue.StandardValue<Lazy<PPossibility>> possibility, TypedValue.StandardValue<PAlternative> alternative) {
      // given Answer = True | False | Error(reason: Unknown | String)
      // t : Cons(head: Answer, tail: List[Answer])
      //   : Cons(head: True | False | Error(reason: Unknown | String), tail: List[Answer])
      List<Lazy<PPossibility>> possibleArgs = possibility.args();
      List<PAlternative> alternativeArgs = alternative.args();
      int nArgs = possibleArgs.size();
      assert nArgs == alternativeArgs.size() : String.format("different sizes: %s <~> %s", possibleArgs, alternativeArgs);
      
      List<Lazy<PPossibility>> resultArgs = new ArrayList<>(nArgs);
      for (int argIdxMutable = 0; argIdxMutable < nArgs; ++argIdxMutable) {
        int argIdx = argIdxMutable;
        Supplier<PPossibility> resultSupplier = () -> {
          PPossibility possibleArg = possibleArgs.get(argIdx).get();
          PAlternative alternativeArg = alternativeArgs.get(argIdx);
          PPossibility resultArg = null;
          if (possibleArg instanceof Disjunction) {
            // e.g. head: True | False | Error(reason: Unknown | String)
            Disjunction possibleArgDisjunction = (Disjunction) possibleArg;
            resultArg = possibleArgDisjunction.subtract(alternativeArg);
          } else if (possibleArg instanceof Simple) {
            // e.g. just True, or just Error(..)
            resultArg = possibleArg.minus(alternativeArg);
          }
          return resultArg;
        };
        resultArgs.add(Lazy.from(resultSupplier));
      }
      
      if (resultArgs.stream().allMatch(none::equals)) {
        return none;
      }
      if (resultArgs.stream().anyMatch(Objects::isNull)) {
        return null;
      }
      return new Simple(possibility.with(resultArgs), argNames);
    }

    @Override
    public String toString() {
      return value.handle(
        large -> large.type().toString(),
        std -> {
          List<Lazy<PPossibility>> args = std.args();
          if (args.isEmpty()) {
            return std.type().toString();
          } else {
            StringBuilder sb = new StringBuilder(std.type().toString()).append('(');
            Iterable<? extends String> namedArgs = EfCollections.zip(argNames, args, (n, a) -> String.format("%s: (%s)", n, a));
            return Joiner.on(", ").appendTo(sb, namedArgs).append(')').toString();
          }
        });
    }

    private PPossibility large() {
      return this; // eventually it'd be nice to decorate this with something like "not value.value()"
    }
  }
  
  public static class Disjunction extends NonEmpty {
    private final List<Lazy<PPossibility.Simple>> options;

    public Disjunction(Collection<Lazy<Simple>> options) {
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
    public String toString() {
      return Joiner.on(" | ").join(options);
    }

    PPossibility subtract(PAlternative alternative) {
      // e.g. disjuction is [ True, False, Error(...) ]
      List<Lazy<Simple>> possibleArgOptions = options();
      for (int optionIdx = 0; optionIdx < possibleArgOptions.size(); ++optionIdx) {
        // e.g. True, or Error(...)
        Simple possibleArgOption = possibleArgOptions.get(optionIdx).get();
        PPossibility matchedArg = possibleArgOption.minus(alternative);
        if (matchedArg != null) {
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
        alternatives.add(new Lazy<>(simple));
      } else if (first instanceof Disjunction) {
        Disjunction disjunction = (Disjunction) first;
        alternatives.addAll(disjunction.options());
      } else if (!none.equals(first)) {
        throw new AssertionError("unknown possibility type: " + first);
      }
      alternatives.addAll(remaining);
      return new Disjunction(alternatives);
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

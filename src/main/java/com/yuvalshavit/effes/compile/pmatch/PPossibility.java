package com.yuvalshavit.effes.compile.pmatch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import com.yuvalshavit.util.Lazy;

public abstract class PPossibility {
  
  @Nullable public abstract PPossibility minus(PAlternative alternative);
  
  private PPossibility() {}

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
    private final PTypedValue<PPossibility> value;

    public Simple(PTypedValue<PPossibility> value) {
      this.value = value;
    }

    @Nullable
    @Override
    protected PPossibility minusAlternative(PAlternative.Simple simpleAlternative) {
      ConcreteTypedValue simpleValue = simpleAlternative.value();
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

    private static PPossibility doSubtract(PTypedValue.StandardValue<PPossibility> possibility, ConcreteTypedValue.StandardValue alternative) {
      // given Answer = True | False | Error(reason: Unknown | String)
      // t : Cons(head: Answer, tail: List[Answer])
      //   : Cons(head: True | False | Error(reason: Unknown | String), tail: List[Answer])
      List<PPossibility> possibleArgs = possibility.args();
      List<PAlternative> alternativeArgs = alternative.args();
      int nArgs = possibleArgs.size();
      assert nArgs == alternativeArgs.size() : String.format("different sizes: %s <~> %s", possibleArgs, alternativeArgs);
      
      List<PPossibility> resultArgs = new ArrayList<>(nArgs);
      for (int argIdx = 0; argIdx < nArgs; ++argIdx) {
        PPossibility possibleArg = possibleArgs.get(argIdx);
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
        resultArgs.add(resultArg);
      }
      
      if (resultArgs.stream().allMatch(none::equals)) {
        return none;
      }
      if (resultArgs.stream().anyMatch(Objects::isNull)) {
        return null;
      }
      return new Simple(possibility.with(resultArgs));
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
      return options.toString();
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

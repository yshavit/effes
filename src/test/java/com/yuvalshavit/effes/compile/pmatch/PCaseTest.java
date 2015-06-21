package com.yuvalshavit.effes.compile.pmatch;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.yuvalshavit.effes.compile.pmatch.PAlternative.Builder;
import static com.yuvalshavit.effes.compile.pmatch.PAlternative.any;
import static com.yuvalshavit.effes.compile.pmatch.PAlternative.simple;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import static org.testng.internal.collections.Pair.create;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.testng.annotations.Test;
import org.testng.internal.collections.Pair;

import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.yuvalshavit.effes.compile.CtorRegistry;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.effes.compile.node.EfVar;
import com.yuvalshavit.util.EfCollections;
import com.yuvalshavit.util.EfFunctions;
import com.yuvalshavit.util.Lazy;

public class PCaseTest {
  private final EfType.SimpleType tTrue;
  private final EfType.SimpleType tFalse;
  private final EfType.DisjunctiveType tBool;
  
  private final EfType.SimpleType tOne;
  private final EfType.SimpleType tNothing;
  private final EfType.DisjunctiveType tMaybe;
  
  private final EfType.SimpleType tEmpty;

  private final CtorRegistry ctors;

  public PCaseTest() {
    ctors = new CtorRegistry();

    tTrue = createSimple("True", ctors);
    tFalse = createSimple("False", ctors);
    tBool = (EfType.DisjunctiveType) EfType.disjunction(tTrue, tFalse);
    
    tOne = createSimple("One", singletonList("T"), t -> {
        EfType.GenericType generic = t.getGenericsDeclr().get(0);
        ctors.setCtorArgs(t, singletonList(EfVar.arg("value", 0, generic)));
      });
    tNothing = createSimple("Nothing", ctors);
    tMaybe = (EfType.DisjunctiveType) EfType.disjunction(tOne, tNothing);
    
    tEmpty = createSimple("Empty", ctors);
  }
  
  private ListTypes buildListType(String name, BiFunction<EfType.GenericType, EfType, List<Pair<String, EfType>>> consBuilder) {
    EfType.SimpleType cons = new EfType.SimpleType(name, singletonList("T"));
    EfType.GenericType genericParam = cons.getGenericsDeclr().get(0);
    EfType.DisjunctiveType list = (EfType.DisjunctiveType) EfType.disjunction(cons, tEmpty);
    Function<EfType, Function<EfType.GenericType, EfType>> reification = g -> Functions.forMap(Collections.singletonMap(genericParam, g))::apply;

    List<Pair<String, EfType>> consArgs = consBuilder.apply(genericParam, list);
    List<EfVar> consVars = new ArrayList<>(consArgs.size());
    for (int i = 0; i < consArgs.size(); ++i) {
      Pair<String, EfType> arg = consArgs.get(i);
      consVars.add(EfVar.arg(arg.first(), i, arg.second()));
    }
    ctors.setCtorArgs(cons, consVars);
    return new ListTypes(cons, list, reification);
  }
  
  @Test
  public void listPattern() {
    ListTypes listTypes = buildListType("Cons", (g, l) -> asList(create("head", g), create("tail", l)));
    EfType.SimpleType cons = listTypes.cons(tBool);
    EfType.DisjunctiveType list = listTypes.list(tBool);

    PPossibility boolsPossibility = PPossibility.from(list, ctors);
    PAlternative firstIsTrue = simple(cons, mTrue(), any()).build(ctors);
    PAlternative secondIsTrue = simple(cons,
      any(),
      simple(cons,
        mTrue(),
        any())
    ).build(ctors);
    PAlternative twoFalses = simple(cons,
      simple(tFalse),
      simple(cons,
        simple(tFalse),
        any())
    ).build(ctors);
    PAlternative empty = simple(tEmpty).build(ctors);
    
    PPossibility result = boolsPossibility.minus(firstIsTrue);
    disjunctionV(
      singleV(cons,
        singleV(tFalse),
        unforcedDisjunctionV(2)
      ),
      unforcedV()
    ).validate(result);
    assertNotNull(result);
    
    PPossibility second = result.minus(secondIsTrue);
    fail("do more validations");
    PPossibility third = second.minus(twoFalses);
    PPossibility last = third.minus(empty);
  }

  @Test
  public void listPatternStartsWithTwoWildcardsInCons() {
    ListTypes listTypes = buildListType("Cons", (g, l) -> asList(create("head", g), create("tail", l)));
    EfType.SimpleType cons = listTypes.cons(tBool);
    EfType.DisjunctiveType list = listTypes.list(tBool);

    PPossibility boolsPossibility = PPossibility.from(list, ctors);
    PAlternative doubleWilds = simple(cons, any(), any()).build(ctors);
    PPossibility result = boolsPossibility.minus(doubleWilds);
    fail("validate " + result);
  }

  @Test
  public void listPatternStartsWithWildcard() {
    ListTypes listTypes = buildListType("Cons", (g, l) -> asList(create("head", g), create("tail", l)));
    EfType.SimpleType cons = listTypes.cons(tBool);
    EfType.DisjunctiveType list = listTypes.list(tBool);

    PPossibility boolsPossibility = PPossibility.from(list, ctors);
    PAlternative fullyWild = any().build(ctors);
    PPossibility result = boolsPossibility.minus(fullyWild);
    noneV().validate(result);
  }
  
  @Test
  public void snocListPattern() {
    // a Snoc List is like a Cons List, but with the args swapped: Cons(tail: ConsList[T], head: T).
    // This means that the infinite recursion is on the first arg (not the second), which tests our lazy evaluation a bit differently.
    ListTypes listTypes = buildListType("Cons", (g, l) -> asList(create("tail", l), create("head", g)));
    EfType.SimpleType snoc = listTypes.cons(tBool);
    EfType.DisjunctiveType list = listTypes.list(tBool);

    PPossibility boolsPossibility = PPossibility.from(list, ctors);
    PAlternative firstIsTrue = simple(snoc, any(), mTrue()).build(ctors);
    PAlternative secondIsTrue = simple(snoc,
      simple(snoc,
        any(),
        mTrue()),
      any()
    ).build(ctors);

    PPossibility result = boolsPossibility.minus(firstIsTrue);
    disjunctionV(
      singleV(snoc,
        unforcedDisjunctionV(2),
        singleV(tFalse)
      ),
      unforcedV()
    ).validate(result);
    assertNotNull(result);

    PPossibility second = result.minus(secondIsTrue);
    fail("validate somehow that it's [False, False, _]: " + second);
  }

  @Test
  public void listOfMaybes() {
    ListTypes listTypes = buildListType("Cons", (g, l) -> asList(create("head", g), create("tail", l)));

    Function<EfType.GenericType, EfType> boolReifiction = EfFunctions.fromGuava(
      Functions.forMap(
        Collections.singletonMap((EfType.GenericType) this.tOne.getParams().get(0), tBool)));
    EfType.SimpleType tOneBool = this.tOne.reify(boolReifiction);
    EfType.DisjunctiveType maybeBool = this.tMaybe.reify(boolReifiction);
    EfType.SimpleType cons = listTypes.cons(maybeBool);
    EfType.DisjunctiveType list = listTypes.list(maybeBool);
    PPossibility possibility = PPossibility.from(list, ctors);

    // case of   [Nothing, _, One(True), _]     = Cons(Nothing, Cons(_, Cons(One(True)), _))
    // expected: [One(_), _]                    = Cons(One(_), _)
    //           [Nothing, _, Nothing, _]       = Cons(Nothing, Cons(_, Cons(Nothing, _)))
    //           [Nothing, _, One(False), _]    = Cons(Nothing, Cons(_, Cons(One(False), _)))
    //           []                             = Empty
    //
    // because of laziness, this comes out as: 
    // Cons(One(_) , _)                             = Cons(..., ...)
    //                                              | Cons(..., Cons(... | ..., ...))
    // Cons(Nothing, Cons(_, Cons(Nothing, _)))     = Cons(..., Cons(... | ..., Cons(Nothing, ... | ...)))
    // Cons(Nothing, Cons(_, Cons(One(False), _)))  = Cons(..., Cons(... | ..., Cons(One(False), ... | ...)))
    // Empty                                        = ...
    
    PAlternative case0 = simple(cons,
      simple(tNothing),
      simple(cons,
        any(),
        simple(cons,
          simple(
            tOneBool,
            simple(tTrue)),
          any()
        )
      )
    ).build(ctors);
    PPossibility afterCase0 = possibility.minus(case0);

    disjunctionV(
      singleV(cons,
        unforcedV(),
        unforcedV()),
      singleV(cons,
        unforcedV(),
        singleV(cons,
            unforcedDisjunctionV(2),
            unforcedV())),
      singleV(cons,
        unforcedV(),
        singleV(cons,
          unforcedDisjunctionV(2),
          singleV(cons,
            singleV(tNothing),
            unforcedDisjunctionV(2)))),
      singleV(cons,
        unforcedV(),
        singleV(cons,
          unforcedDisjunctionV(2),
          singleV(cons,
            singleV(tOneBool, singleV(tFalse)),
            unforcedDisjunctionV(2)))),
      unforcedV()
    ).validate(afterCase0);
  }
  
  private Builder mTrue() {
    return simple(tTrue);
  }

  @Test
  public void trueNoArgs() {
    assertNotNull(mTrue().build(ctors));
  }

  @Test
  public void trueWithAnyArg() {
    assertNull(simple(tTrue, any()).build(ctors));
  }
  
  @Test
  public void trueWithFalseArg() {
    assertNull(simple(tTrue, simple(tFalse)).build(ctors));
  }

  @Test
  public void createInfinitePossibilities() {
    EfType.SimpleType tInfiniteBools = new EfType.SimpleType("InfiniteInts", Collections.emptyList());
    ctors.setCtorArgs(
      tInfiniteBools,
      asList(
        EfVar.arg("head", 0, tBool),
        EfVar.arg("tail", 1, tInfiniteBools)));
    
    PPossibility from = PPossibility.from(tInfiniteBools, ctors);
    assertNotNull(from.toString()); // this is really to check for a stack overflow due to infinite recursion
  }
  
  private static class Validator {
    private final String desc;
    private final Consumer<Lazy<? extends PPossibility>> validator;

    public Validator(String desc, Consumer<Lazy<? extends PPossibility>> validator) {
      this.desc = desc;
      this.validator = validator;
    }

    public void validate(PPossibility possibility) {
      try {
        validate(Lazy.forced(possibility));
      } catch (Exception e) {
        throw new RuntimeException(formatFailure(possibility), e);
      } catch (AssertionError e) {
        throw new AssertionError(formatFailure(possibility), e);
      }
    }

    private String formatFailure(PPossibility possibility) {
      return String.format("%nvalidation expected: %s%n            but saw: %s", desc, possibility.toString(false));
    }

    public void validate(Lazy<? extends PPossibility> possibility) {
      validator.accept(possibility);
    }

    @Override
    public String toString() {
      return desc;
    }
  }
  
  private static Consumer<Lazy<? extends PPossibility>> forced(Consumer<? super PPossibility> validator) {
    return lazyPossibility -> {
      assertFalse(lazyPossibility.isUnforced(), "possibility was unforced");
      validator.accept(lazyPossibility.get());
    };
  }
  
  private static Validator singleV(EfType.SimpleType type, Validator... args) {
    String desc = type.getName();
    if (args.length != 0) {
      StringBuilder sb = new StringBuilder(desc).append('(');
      desc = Joiner.on(", ").appendTo(sb, args).append(')').toString();
    }
    return new Validator(
      desc,
      forced(
        actual -> {
          assertThat(actual, instanceOf(PPossibility.Simple.class));
          PPossibility.Simple actualSimple = (PPossibility.Simple) actual;
          assertFalse(actualSimple.typedAndArgs().isUnforced(), "not forced");
          TypedValue<PPossibility> typedValue = actualSimple.typedAndArgs().get();
          assertThat(typedValue.type(), equalTo(type));
          typedValue.consume(
            l -> assertEquals(args.length, 0), s -> {
              assertEquals(s.args().size(), args.length);
              EfCollections.zipC(asList(args), s.args(), Validator::validate);
            });
        }));
  }

  private static Validator disjunctionV(Validator... alternatives) {
    return new Validator(
      Joiner.on(" | ").join(alternatives),
      forced(
        actual -> {
          assertThat(actual, instanceOf(PPossibility.Disjunction.class));
          PPossibility.Disjunction actualDisjunction = (PPossibility.Disjunction) actual;
          List<Lazy<PPossibility.Simple>> options = actualDisjunction.options();
          assertEquals(options.size(), alternatives.length);
          // ensure that each validator works on exactly one option
          for (Validator alternative : alternatives) {
            ValidationResults validationResults = new ValidationResults();
            for (Lazy<PPossibility.Simple> option : options) {
              try {
                alternative.validate(option);
                validationResults.succeeded.add(option);
              } catch (AssertionError e) {
                validationResults.failed.add(option);
              }
            }
            assertTrue(validationResults.exactlyOneSuccess(), "results for alternative " + alternative + ": "+ validationResults);
          }
          EfCollections.zipC(asList(alternatives), options, Validator::validate);
        }));
  }
  
  private static Validator unforcedDisjunctionV(int n) {
    assertThat("value", n, greaterThan(1));
    Validator[] validators = new Validator[n];
    Arrays.fill(validators, unforcedV());
    return disjunctionV(validators);
  }
  
  private static Validator unforcedV() {
    return new Validator(Lazy.UNFORCED_DESC, actual -> assertTrue(actual.isUnforced(), "should have been unforced: " + actual));
  }
  
  private static Validator noneV() {
    return new Validator(PPossibility.none.toString(), forced(PPossibility.none::equals));
  }

  private static EfType.SimpleType createSimple(String name, CtorRegistry ctors) {
    return createSimple(name, Collections.emptyList(), t -> ctors.setCtorArgs(t, Collections.emptyList()));
  }

  private static EfType.SimpleType createSimple(String name, List<String> genericParams, Consumer<EfType.SimpleType> ctorsRegistration) {
    EfType.SimpleType type = new EfType.SimpleType(name, genericParams);
    ctorsRegistration.accept(type);
    return type;
  }

  private static class ListTypes {
    private final EfType.SimpleType cons;
    private final EfType.DisjunctiveType list;
    private final Function<EfType, Function<EfType.GenericType, EfType>> reification;
    public ListTypes(EfType.SimpleType cons, EfType.DisjunctiveType list, Function<EfType, Function<EfType.GenericType, EfType>> reification) {
      this.cons = cons;
      this.list = list;
      this.reification = reification;
    }

    EfType.SimpleType cons(EfType genericArg) {
      checkNotNull(genericArg);
      return cons.reify(reification.apply(genericArg));
    }

    EfType.DisjunctiveType list(EfType genericArg) {
      checkNotNull(genericArg);
      return list.reify(reification.apply(genericArg));
    }
  }

  private static class ValidationResults {
    final Collection<Lazy<PPossibility.Simple>> succeeded = new ArrayList<>();
    final Collection<Lazy<PPossibility.Simple>> failed = new ArrayList<>();

    public boolean exactlyOneSuccess() {
      return succeeded.size() == 1;
    }

    @Override
    public String toString() {
      return String.format("matched=%s, failed=%s", succeeded, failed);
    }
  }
}

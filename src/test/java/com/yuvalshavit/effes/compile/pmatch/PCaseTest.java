package com.yuvalshavit.effes.compile.pmatch;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.yuvalshavit.effes.compile.pmatch.PAlternative.Builder;
import static com.yuvalshavit.effes.compile.pmatch.PAlternative.any;
import static com.yuvalshavit.effes.compile.pmatch.PAlternative.simple;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.internal.collections.Pair.create;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.testng.annotations.Test;
import org.testng.internal.collections.Pair;

import com.google.common.base.Functions;
import com.google.common.collect.Lists;
import com.yuvalshavit.effes.compile.node.CtorArg;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.effes.compile.node.Reifications;
import com.yuvalshavit.util.EfAssertions;
import com.yuvalshavit.util.EfFunctions;

public class PCaseTest {
  
  private final EfType.SimpleType tTrue;
  private final EfType.SimpleType tFalse;
  private final EfType.DisjunctiveType tBool;
  
  private final EfType.SimpleType tOne;
  private final EfType.SimpleType tNothing;
  private final EfType.DisjunctiveType tMaybe;
  
  private final EfType.SimpleType tEmpty;

  public PCaseTest() {
    tTrue = createSimple("True");
    tFalse = createSimple("False");
    tBool = (EfType.DisjunctiveType) EfType.disjunction(tTrue, tFalse);
    
    tOne = createSimple("One", singletonList("T"), t -> {
        EfType.GenericType generic = t.getGenericsDeclr().get(0);
        t.setCtorArgs(singletonList(new CtorArg(0, "value", generic)));
      });
    tNothing = createSimple("Nothing");
    tMaybe = (EfType.DisjunctiveType) EfType.disjunction(tOne, tNothing);
    
    tEmpty = createSimple("Empty");
  }
  
  private ListTypes buildListType(String name, BiFunction<EfType.GenericType, EfType, List<Pair<String, EfType>>> consBuilder) {
    EfType.SimpleType cons = new EfType.SimpleType(name, singletonList("T"));
    EfType.GenericType genericParam = cons.getGenericsDeclr().get(0);
    EfType.DisjunctiveType list = (EfType.DisjunctiveType) EfType.disjunction(cons, tEmpty);
    Function<EfType, Function<EfType.GenericType, EfType>> reification = g -> Functions.forMap(Collections.singletonMap(genericParam, g))::apply;

    List<Pair<String, EfType>> consArgs = consBuilder.apply(genericParam, list);
    List<CtorArg> consVars = new ArrayList<>(consArgs.size());
    for (int i = 0; i < consArgs.size(); ++i) {
      Pair<String, EfType> arg = consArgs.get(i);
      consVars.add(new CtorArg(i, arg.first(), arg::second));
    }
    cons.setCtorArgs(consVars);
    return new ListTypes(cons, list, reification);
  }
  
  @Test
  public void listPattern() {
    ListTypes listTypes = buildListType("Cons", (g, l) -> asList(create("head", g), create("tail", l)));
    EfType.SimpleType cons = listTypes.cons(tBool);
    EfType list = listTypes.list(tBool);
    
    PPossibility.TypedPossibility<?> boolsPossibility = PPossibility.from(list);
    PAlternative firstIsTrue = simple(cons, mTrue(), any("a")).build();
    // case     List[Bool]
    // of       Cons(True, a)
    // 
    // result   Cons(False, _) | Empty
    PAlternativeSubtractionResult result = firstIsTrue.subtractFrom(boolsPossibility);
    check(result,
      "Cons[α](False, ...)",
      "Empty",
      "---",
      "α: False | True",
      "---",
      "bound a: Cons[False | True] | Empty");
  }

  @Test
  public void simpleOne() {
    EfType.SimpleType oneBool = Reifications.reifyOnlyGenericOf(tOne).to(tBool);
    PPossibility.TypedPossibility<?> possibility = PPossibility.from(oneBool);

    PAlternative oneOfTrue = simple(Reifications.reifyOnlyGenericOf(tOne).to(tTrue), mTrue()).build();
    PAlternativeSubtractionResult result = oneOfTrue.subtractFrom(possibility);
    check(result,
      "One[α](False)",
      "---",
      "α: False");
  }

  @Test
  public void listPatternStartsWithTwoWildcardsInCons() {
    ListTypes listTypes = buildListType("Cons", (g, l) -> asList(create("head", g), create("tail", l)));
    EfType.SimpleType cons = listTypes.cons(tBool);
    EfType list = listTypes.list(tBool);

    PPossibility.TypedPossibility<?> boolsPossibility = PPossibility.from(list);
    PAlternative doubleWilds = simple(cons, any(), any()).build();
    PAlternativeSubtractionResult result = doubleWilds.subtractFrom(boolsPossibility);
    check(result, "Empty");
  }

  @Test
  public void listPatternStartsWithWildcards() {
    ListTypes listTypes = buildListType("Cons", (g, l) -> asList(create("head", g), create("tail", l)));
    EfType list = listTypes.list(tBool);

    PPossibility.TypedPossibility<?> boolsPossibility = PPossibility.from(list);
    PAlternative doubleWilds = any().build();
    PAlternativeSubtractionResult result = doubleWilds.subtractFrom(boolsPossibility);
    assertNotNull(result);
    assertEquals(result.possibility(), PPossibility.none);
  }

  @Test
  public void listPatternStartsWithWildcard() {
    ListTypes listTypes = buildListType("Cons", (g, l) -> asList(create("head", g), create("tail", l)));
    EfType list = listTypes.list(tBool);

    PPossibility.TypedPossibility<?> boolsPossibility = PPossibility.from(list);
    PAlternative fullyWild = any().build();
    PAlternativeSubtractionResult result = fullyWild.subtractFrom(boolsPossibility);
    assertNotNull(result);
    assertEquals(result.possibility(), PPossibility.none);
  }
  
  @Test
  public void snocListPattern() {
    // a Snoc List is like a Cons List, but with the args swapped: Cons(tail: ConsList[T], head: T).
    // This means that the infinite recursion is on the first arg (not the second), which tests our lazy evaluation a bit differently.
    ListTypes listTypes = buildListType("Cons", (g, l) -> asList(create("tail", l), create("head", g)));
    EfType.SimpleType snoc = listTypes.cons(tBool);
    EfType list = listTypes.list(tBool);

    PPossibility.TypedPossibility<?> boolsPossibility = PPossibility.from(list);
    PAlternative firstIsTrue = simple(snoc, any(), mTrue()).build();
    PAlternative secondIsTrue = simple(snoc,
      simple(snoc,
        any(),
        mTrue()),
      any()
    ).build();

    PAlternativeSubtractionResult result = firstIsTrue.subtractFrom(boolsPossibility);
    check(result,
      "Cons[α](..., False)",
      "Empty",
      "---",
      "α: False | True");
    assertNotNull(result);
    PAlternativeSubtractionResult second = secondIsTrue.subtractFrom(result);
    check(second,
      "Cons[α](Cons[α](..., False), False)",
      "Cons[β](Empty, False)",
      "Empty",
      "---",
      "α: False | True",
      "β: False");
  }

  @Test
  public void listOfMaybes() {
    ListTypes listTypes = buildListType("Cons", (g, l) -> asList(create("head", g), create("tail", l)));

    Function<EfType.GenericType, EfType> boolReifiction = EfFunctions.fromGuava(
      Functions.forMap(
        Collections.singletonMap((EfType.GenericType) this.tOne.getParams().get(0), tBool)));
    EfType.SimpleType tOneBool = this.tOne.reify(boolReifiction);
    EfType maybeBool = this.tMaybe.reify(boolReifiction);
    EfType.SimpleType cons = listTypes.cons(maybeBool);
    EfType list = listTypes.list(maybeBool);
    PPossibility.TypedPossibility<?> possibility = PPossibility.from(list);

    // case of   [Nothing, foo, One(True), _]     = Cons(Nothing, Cons(foo, Cons(One(True)), _))
    //
    // expected: [One(_), _]                    = Cons(One(_), _)
    //           [Nothing, _, Nothing, _]       = Cons(Nothing, Cons(_, Cons(Nothing, _)))
    //           [Nothing, _, One(False), _]    = Cons(Nothing, Cons(_, Cons(One(False), _)))
    //           [Nothing, _]                   = Cons(Nothing, _)
    //           []                             = Empty
    //
    // with foo: Nothing | One[False | True]
    //
    // This gets expanded a bit
    
    PAlternative case0 = simple(cons,
      simple(tNothing),
      simple(cons,
        any("foo"),
        simple(cons,
          simple(
            tOneBool,
            simple(tTrue)),
          any()
        )
      )
    ).build();
    
    PAlternativeSubtractionResult afterCase0 = case0.subtractFrom(possibility);
    assertNotNull(afterCase0);
    check(
      afterCase0,
      "Cons[α](Nothing, Cons[α](..., Cons[α](Nothing, ...)))",
      "Cons[β](Nothing, Cons[β](..., Cons[β](One[γ](False), ...)))",
      "Cons[α](Nothing, Cons[α](..., Empty))",
      "Cons[δ](Nothing, Empty)",
      "Cons[ε](One[ζ](...), Cons[ε](..., Cons[ε](One[η](True), ...)))",
      "Cons[α](One[ζ](...), Cons[α](..., Cons[α](Nothing, ...)))",
      "Cons[β](One[ζ](...), Cons[β](..., Cons[β](One[γ](False), ...)))",
      "Cons[α](One[ζ](...), Cons[α](..., Empty))",
      "Cons[θ](One[ζ](...), Empty)",
      "Empty",
      "---",
      "α: Nothing | One[False | True]",
      "β: Nothing | One[False | True] | One[False]",
      "γ: False",
      "δ: Nothing",
      "ε: Nothing | One[False | True] | One[True]",
      "ζ: False | True",
      "η: True",
      "θ: One[False | True]",
      "---",
      "bound foo: Nothing | One[False | True]");

    // So, now we have T as a disjunction of: 
    //           [One(_), _]                    = Cons(One(_), _)
    //           [Nothing, _, Nothing, _]       = Cons(Nothing, Cons(_, Cons(Nothing, _)))
    //           [Nothing, _, One(False), _]    = Cons(Nothing, Cons(_, Cons(One(False), _)))
    //           [Nothing, _]                   = Cons(Nothing, _)
    //           []                             = Empty
    //
    // And we do
    // case of  [One(True | False), _]          = Cons(One(True | False), _)
    //
    // This means that the One(_) alternative is fully matched, and the others are unaffected:
    //
    // result:   [Nothing, _, Nothing, _]       = Cons(Nothing, Cons(_, Cons(Nothing, _)))
    //           [Nothing, _, One(False), _]    = Cons(Nothing, Cons(_, Cons(One(False), _)))
    //           [Nothing, _]                   = Cons(Nothing, _)
    //           []                             = Empty

    PAlternative case1 = simple(cons,
      simple(tOneBool, any("ft")),
      any()
    ).build();

    PAlternativeSubtractionResult afterCase1 = case1.subtractFrom(afterCase0);
    assertNotNull(afterCase1);
    check(afterCase1,
      "Cons[α](Nothing, Cons[α](..., Cons[α](Nothing, ...)))",
      "Cons[β](Nothing, Cons[β](..., Cons[β](One[γ](False), ...)))",
      "Cons[α](Nothing, Cons[α](..., Empty))",
      "Cons[δ](Nothing, Empty)",
      "Empty",
      "---",
      "α: Nothing | One[False | True]",
      "β: Nothing | One[False | True] | One[False]",
      "γ: False",
      "δ: Nothing",
      "---",
      "bound ft: False | True");
    
    // Now we match against Cons(Nothing, _). We should expect Empty back
    PAlternative case2 = simple(cons,
      simple(tNothing),
      any()
    ).build();

    PAlternativeSubtractionResult afterCase2 = case2.subtractFrom(afterCase1);
    assertNotNull(afterCase2);
    check(afterCase2,
      "Empty");
    
    // Now match against Empty, which is the only alternative left
    PAlternative case3 = simple(tEmpty).build();
    
    PAlternativeSubtractionResult afterCase3 = case3.subtractFrom(afterCase2);
    assertNotNull(afterCase3);
    assertEquals(afterCase3.possibility(), PPossibility.none);
  }

  @Test
  public void consTrueMatchedAgainstConsFalse() {
    ListTypes listTypes = buildListType("Cons", (g, l) -> asList(create("head", g), create("tail", l)));
    EfType listOfTrue = listTypes.list(tTrue);
    EfType.SimpleType consOfFalse = listTypes.cons(tFalse);
    PPossibility.TypedPossibility<?> possibility = PPossibility.from(listOfTrue);
    
    // case Cons[True] of Cons[True](_, _)
    PAlternative caseOfConsFalse = simple(consOfFalse,
      simple(tFalse),
      any()
    ).build();

    PAlternativeSubtractionResult afterCase = caseOfConsFalse.subtractFrom(possibility);
    assertNull(afterCase);
  }
  
  @Test
  public void consTrueMatchedAgainstConsFalseOfAny() {
    ListTypes listTypes = buildListType("Cons", (g, l) -> asList(create("head", g), create("tail", l)));
    EfType listOfTrue = listTypes.list(tTrue);
    EfType.SimpleType consOfFalse = listTypes.cons(tFalse);
    PPossibility.TypedPossibility<?> possibility = PPossibility.from(listOfTrue);

    // case Cons[True] of Cons[False](_, _)
    PAlternative caseOfConsFalse = simple(consOfFalse,
      any(),
      any()
    ).build();

    PAlternativeSubtractionResult afterCase = caseOfConsFalse.subtractFrom(possibility);
    assertNotNull(afterCase);
    
    check(afterCase,
      "Empty");
  }

  @Test
  public void boundWildcardUsedTwice() {
    EfType.SimpleType multiBox = createSimple("MultiBox", Collections.emptyList(), t -> t.setCtorArgs(Arrays.asList(
      new CtorArg(0, "myTrue", tTrue),
      new CtorArg(1, "myFalse", tFalse),
      new CtorArg(2, "myNothing", tNothing))));

    PAlternative alternative = simple(multiBox,
      any("a"),
      any("a"),
      any("b")
    ).build();
    PAlternativeSubtractionResult result = alternative.subtractFrom(PPossibility.from(multiBox));
    check(result,
      "∅",
      "---",
      "bound a: False | True",
      "bound b: Nothing");
  }

  @Test
  public void wildcardFromNone() {
    PAlternative any = any().build();
    PAlternativeSubtractionResult subtraction = any.subtractFrom(new PAlternativeSubtractionResult(PPossibility.none, null, Collections.emptyMap()));
    assertNull(subtraction);
  }

  @Test
  public void concreteFromNone() {
    PAlternative any = simple(tNothing).build();
    PAlternativeSubtractionResult subtraction = any.subtractFrom(new PAlternativeSubtractionResult(PPossibility.none, null, Collections.emptyMap()));
    assertNull(subtraction);
  }

  @Test
  public void nothingFromEmpty() {
    PAlternative any = simple(tNothing).build();
    PAlternativeSubtractionResult subtraction = any.subtractFrom(PPossibility.from(tEmpty));
    assertNull(subtraction);
  }
  
  private void check(PAlternativeSubtractionResult possibility, String... expected) {
    assertNotNull(possibility, "null possibility (alternative wasn't matched)");
    List<String> expectedList = Lists.newArrayList(expected);
    
    List<String> actualList = PPossibilities.toStrings(possibility.possibility());
    if (!possibility.bindings().isEmpty()) {
      actualList.add("---");
      possibility.bindings().forEach((k, v) -> actualList.add(String.format("bound %s: %s", k, v)));
    }
    EfAssertions.equalLists(actualList, expectedList);
  }

  private Builder mTrue() {
    return simple(tTrue);
  }

  @Test
  public void trueNoArgs() {
    assertNotNull(mTrue().build());
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void trueWithAnyArg() {
    simple(tTrue, any()).build();
  }
  
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void trueWithFalseArg() {
    simple(tTrue, simple(tFalse)).build();
  }

  @Test
  public void createInfinitePossibilities() {
    EfType.SimpleType tInfiniteBools = new EfType.SimpleType("InfiniteInts", Collections.emptyList());
    tInfiniteBools.setCtorArgs(
      asList(
        new CtorArg(0, "head", tBool),
        new CtorArg(1, "tail", tInfiniteBools)));

    PPossibility from = PPossibility.from(tInfiniteBools);
    assertNotNull(from.toString()); // this is really to check for a stack overflow due to infinite recursion
  }

  private static EfType.SimpleType createSimple(String name) {
    return createSimple(name, Collections.emptyList(), t -> t.setCtorArgs(Collections.emptyList()));
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

    EfType list(EfType genericArg) {
      checkNotNull(genericArg);
      return list.reify(reification.apply(genericArg));
    }
  }
}

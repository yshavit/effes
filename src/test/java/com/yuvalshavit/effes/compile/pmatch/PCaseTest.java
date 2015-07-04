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
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.testng.annotations.Test;
import org.testng.internal.collections.Pair;

import com.google.common.base.Functions;
import com.google.common.collect.Lists;
import com.yuvalshavit.effes.compile.CtorRegistry;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.effes.compile.node.EfVar;
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
    // case     List[Bool]
    // of       Cons(True, _)
    // 
    // result   Cons(False, _) | Empty
    PPossibility result = firstIsTrue.subtractFrom(boolsPossibility);
    check(result,
      "Cons(False, ...)",
      "Empty");
  }

  @Test
  public void listPatternStartsWithTwoWildcardsInCons() {
    ListTypes listTypes = buildListType("Cons", (g, l) -> asList(create("head", g), create("tail", l)));
    EfType.SimpleType cons = listTypes.cons(tBool);
    EfType.DisjunctiveType list = listTypes.list(tBool);

    PPossibility boolsPossibility = PPossibility.from(list, ctors);
    PAlternative doubleWilds = simple(cons, any(), any()).build(ctors);
    PPossibility result = doubleWilds.subtractFrom(boolsPossibility);
    assertEquals(result, PPossibility.none);
  }

  @Test
  public void listPatternStartsWithWildcard() {
    ListTypes listTypes = buildListType("Cons", (g, l) -> asList(create("head", g), create("tail", l)));
    EfType.DisjunctiveType list = listTypes.list(tBool);

    PPossibility boolsPossibility = PPossibility.from(list, ctors);
    PAlternative fullyWild = any().build(ctors);
    PPossibility result = fullyWild.subtractFrom(boolsPossibility);
    assertEquals(result, PPossibility.none);
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

    PPossibility result = firstIsTrue.subtractFrom(boolsPossibility);
    check(result,
      "Cons(..., False)",
      "Empty");
    PPossibility second = secondIsTrue.subtractFrom(result);
    check(second,
      "Cons(Empty, False)",
      "Cons(Cons(..., False), False)",
      "Empty");
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
    //
    // expected: [One(_), _]                    = Cons(One(_), _)
    //           [Nothing, _, Nothing, _]       = Cons(Nothing, Cons(_, Cons(Nothing, _)))
    //           [Nothing, _, One(False), _]    = Cons(Nothing, Cons(_, Cons(One(False), _)))
    //           []                             = Empty
    //
    // This gets expanded a bit
    
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
    
    PPossibility afterCase0 = case0.subtractFrom(possibility);
    check(afterCase0,
      "Cons(Nothing, Cons(..., Cons(Nothing, ...)))",
      "Cons(Nothing, Cons(..., Cons(One(False), ...)))",
      "Cons(Nothing, Cons(..., Empty))",
      "Cons(Nothing, Empty)",
      "Cons(One(...), Cons(..., Cons(One(True), ...)))",
      "Cons(One(...), Cons(..., Cons(Nothing, ...)))",
      "Cons(One(...), Cons(..., Cons(One(False), ...)))",
      "Cons(One(...), Cons(..., Empty))",
      "Cons(One(...), Empty)",
      "Empty");
    
    PAlternative case1 = simple(cons,
      simple(tOneBool, any()),
      any()
    ).build(ctors);

    PPossibility afterCase1 = case1.subtractFrom(afterCase0);
    check(afterCase1,
      "Cons(Nothing, Cons(..., Cons(Nothing, ...)))",
      "Cons(Nothing, Cons(..., Cons(One(False), ...)))",
      "Cons(Nothing, Cons(..., Empty))",
      "Cons(Nothing, Empty)",
      "Empty");
    
    PAlternative case2 = simple(cons,
      simple(tNothing),
      any()
    ).build(ctors);

    PPossibility afterCase2 = case2.subtractFrom(afterCase1);
    check(afterCase2,
      "Empty");
    
    PAlternative case3 = simple(tEmpty).build(ctors);
    
    PPossibility afterCase3 = case3.subtractFrom(afterCase2);
    
    assertEquals(afterCase3, PPossibility.none);
  }

  @Test
  public void wildcardFromNone() {
    PAlternative any = any().build(ctors);
    PPossibility subtraction = any.subtractFrom(PPossibility.none);
    assertNull(subtraction);
  }

  @Test
  public void concreteFromNone() {
    PAlternative any = simple(tNothing).build(ctors);
    PPossibility subtraction = any.subtractFrom(PPossibility.none);
    assertNull(subtraction);
  }

  @Test
  public void nothingFromEmpty() {
    PAlternative any = simple(tNothing).build(ctors);
    PPossibility subtraction = any.subtractFrom(PPossibility.from(tEmpty, ctors));
    assertNull(subtraction);
  }
  

  private void check(PPossibility possibility, String... expected) {
    List<String> expectedList = Lists.newArrayList(expected);
    Collections.sort(expectedList);
    List<String> actualList = PPossibilities.toStrings(possibility);
    EfAssertions.equalLists(actualList, expectedList);
  }

  private Builder mTrue() {
    return simple(tTrue);
  }

  @Test
  public void trueNoArgs() {
    assertNotNull(mTrue().build(ctors));
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void trueWithAnyArg() {
    simple(tTrue, any()).build(ctors);
  }
  
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void trueWithFalseArg() {
    simple(tTrue, simple(tFalse)).build(ctors);
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
}

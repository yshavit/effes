package com.yuvalshavit.effes.compile.pmatch;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.yuvalshavit.effes.compile.pmatch.PAlternative.*;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
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
import com.google.common.base.Joiner;
import com.yuvalshavit.effes.compile.CtorRegistry;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.effes.compile.node.EfVar;
import com.yuvalshavit.util.EfCollections;
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
  
  private ListTypes buildListType(String name, EfType genericArg, BiFunction<EfType.GenericType, EfType, List<Pair<String, EfType>>> consBuilder) {
    EfType.SimpleType cons = new EfType.SimpleType(name, singletonList("T"));
    EfType.GenericType genericParam = cons.getGenericsDeclr().get(0);
    EfType.DisjunctiveType list = (EfType.DisjunctiveType) EfType.disjunction(cons, tEmpty);
    Function<EfType.GenericType, EfType> reification = Functions.forMap(Collections.singletonMap(genericParam, genericArg))::apply;

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
    ListTypes listTypes = buildListType("Cons", tBool, (g, l) -> asList(create("head", g), create("tail", l)));
    EfType.SimpleType cons = listTypes.cons(tBool);
    EfType.DisjunctiveType list = listTypes.list(tBool);

    PPossibility boolsPossibility = PPossibility.from(list, ctors);
    PAlternative firstIsTrue = simple(cons, mTrue(), any()).build(ctors);
    PAlternative secondIsTrue = simple(
      cons,
      any(),
      simple(
        cons,
        mTrue(),
        any())
    ).build(ctors);

    PPossibility result = boolsPossibility.minus(firstIsTrue);
    disjunctionV(
      singleV(cons,
        singleV(tFalse),
        unforcedV()
      ),
      unforcedV()
    ).validate(result);
    assertNotNull(result);
    
    PPossibility second = result.minus(secondIsTrue);
    fail("validate somehow that it's [False, False, _]: " + second);
  }
  
  @Test
  public void snocListPattern() {
    // a Snoc List is like a Cons List, but with the args swapped: Cons(tail: ConsList[T], head: T).
    // This means that the infinite recursion is on the first arg (not the second), which tests our lazy evaluation a bit differently.
    ListTypes listTypes = buildListType("Cons", tBool, (g, l) -> asList(create("tail", l), create("head", g)));
    EfType.SimpleType snoc = listTypes.cons(tBool);
    EfType.DisjunctiveType list = listTypes.list(tBool);

    PPossibility boolsPossibility = PPossibility.from(list, ctors);
    PAlternative firstIsTrue = simple(snoc, mTrue(), any()).build(ctors);
    PAlternative secondIsTrue = simple(
      snoc,
      simple(
        snoc,
        any(),
        mTrue()),
      any()
    ).build(ctors);

    PPossibility result = boolsPossibility.minus(firstIsTrue);
    disjunctionV(
      singleV(snoc,
        unforcedV(),
        singleV(tFalse)
      ),
      unforcedV()
    ).validate(result);
    assertNotNull(result);

    PPossibility second = result.minus(secondIsTrue);
    fail("validate somehow that it's [False, False, _]: " + second);
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
  
  private static Validator noneV() {
    return new Validator(PPossibility.none.toString(), actual -> assertSame(actual, PPossibility.none));
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
          TypedValue<PPossibility> typedvalue = actualSimple.typedAndArgs().get();
          assertThat(typedvalue.type(), equalTo(type));
          typedvalue.consume(
            l -> assertEquals(args.length, 0), s -> {
              assertEquals(s.args().size(), args.length);
              EfCollections.zipC(asList(args), s.args(), Validator::validate);
            });
        }));
  }
  
  private static Validator disjunctionV(Validator... alternatives) {
    return new Validator(
      Joiner.on(" | ").join(alternatives),
      forced(actual -> {
        assertThat(actual, instanceOf(PPossibility.Disjunction.class));
        PPossibility.Disjunction actualDisjunction = (PPossibility.Disjunction) actual;
        List<Lazy<PPossibility.Simple>> options = actualDisjunction.options();
        assertEquals(options.size(), alternatives.length);
        EfCollections.zipC(asList(alternatives), options, Validator::validate);
      }));
  }
  
  private static Validator unforcedV() {
    return new Validator(Lazy.UNFORCED_DESC, actual -> assertTrue(actual.isUnforced(), "should have been unforced: " + actual));
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
    private final Function<EfType.GenericType, EfType> reification;

    public ListTypes(EfType.SimpleType cons, EfType.DisjunctiveType list, Function<EfType.GenericType, EfType> reification) {
      this.cons = cons;
      this.list = list;
      this.reification = reification;
    }

    EfType.SimpleType cons(EfType genericArg) {
      checkNotNull(genericArg);
      return cons.reify(reification);
    }

    EfType.DisjunctiveType list(EfType genericArg) {
      checkNotNull(genericArg);
      return list.reify(reification);
    }
  }
}

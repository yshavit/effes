package com.yuvalshavit.effes.compile.pmatch;

import static com.yuvalshavit.effes.compile.pmatch.PAlternative.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.testng.annotations.Test;

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
  private final EfType.SimpleType tEmpty;

  private final CtorRegistry ctors;

  public PCaseTest() {
    tTrue = new EfType.SimpleType("True", Collections.emptyList());
    tFalse = new EfType.SimpleType("False", Collections.emptyList());
    tBool = (EfType.DisjunctiveType) EfType.disjunction(tTrue, tFalse);
    
    ctors = new CtorRegistry();
    ctors.setCtorArgs(tTrue, Collections.emptyList());
    ctors.setCtorArgs(tFalse, Collections.emptyList());
    
    tEmpty = new EfType.SimpleType("Empty", Collections.emptyList());
    ctors.setCtorArgs(tEmpty, Collections.emptyList());
  }

  @Test
  public void listPattern() {
    EfType.SimpleType tCons = new EfType.SimpleType("Cons", Collections.singletonList("T"));
    EfType.GenericType tListGeneric = tCons.getGenericsDeclr().get(0);
    Function<EfType.GenericType, EfType> listReification = Functions.forMap(Collections.singletonMap(tListGeneric, tBool))::apply;
    
    EfType.DisjunctiveType tList = (EfType.DisjunctiveType) EfType.disjunction(tCons, tEmpty);
    EfType list = tList.reify(listReification);
    EfType.SimpleType cons = tCons.reify(listReification);
    ctors.setCtorArgs(
      tCons,
      Arrays.asList(
        EfVar.arg("head", 0, tListGeneric),
        EfVar.arg("tail", 1, tList)));

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
    EfType.SimpleType tSnoc = new EfType.SimpleType("Snoc", Collections.singletonList("T")); // a Cons but with args swapped: defines (tail, head)
    EfType.GenericType tSnocListGeneric = tSnoc.getGenericsDeclr().get(0);
    Function<EfType.GenericType, EfType> snocReification = Functions.forMap(Collections.singletonMap(tSnocListGeneric, tBool))::apply;

    EfType.DisjunctiveType tSnocList = (EfType.DisjunctiveType) EfType.disjunction(tSnoc, tEmpty);
    EfType tBoolsSnocList = tSnocList.reify(snocReification);
    EfType.SimpleType tBoolSnoc = tSnoc.reify(snocReification);
    ctors.setCtorArgs(
      tSnoc,
      Arrays.asList(
        EfVar.arg("tail", 0, tSnocList),
        EfVar.arg("head", 1, tSnocListGeneric)));
    
    PPossibility boolsPossibility = PPossibility.from(tBoolsSnocList, ctors);
    PAlternative firstIsTrue = simple(tBoolSnoc, mTrue(), any()).build(ctors);
    PAlternative secondIsTrue = simple(
      tBoolSnoc,
      simple(
        tBoolSnoc,
        any(),
        mTrue()),
      any()
    ).build(ctors);

    PPossibility result = boolsPossibility.minus(firstIsTrue);
    disjunctionV(
      singleV(tBoolSnoc,
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
      Arrays.asList(
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
              EfCollections.zipC(Arrays.asList(args), s.args(), Validator::validate);
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
        EfCollections.zipC(Arrays.asList(alternatives), options, Validator::validate);
      }));
  }
  
  private static Validator unforcedV() {
    return new Validator(Lazy.UNFORCED_DESC, actual -> assertTrue(actual.isUnforced(), "should have been unforced: " + actual));
  }
}

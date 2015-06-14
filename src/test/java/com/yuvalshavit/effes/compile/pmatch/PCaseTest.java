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
  private final EfType.SimpleType tInfiniteBools;

  private final EfType.SimpleType tCons;
  private final EfType.SimpleType tEmpty;
  private final EfType.DisjunctiveType tList;
  private EfType.SimpleType tBoolCons;

  private final EfType.GenericType tListGeneric;
  private final CtorRegistry ctors;
  private EfType tBoolsList;

  public PCaseTest() {
    tTrue = new EfType.SimpleType("True", Collections.emptyList());
    tFalse = new EfType.SimpleType("False", Collections.emptyList());
    tBool = (EfType.DisjunctiveType) EfType.disjunction(tTrue, tFalse);
    tInfiniteBools = new EfType.SimpleType("InfiniteInts", Collections.emptyList());
    
    ctors = new CtorRegistry();
    ctors.setCtorArgs(tTrue, Collections.emptyList());
    ctors.setCtorArgs(tFalse, Collections.emptyList());
    ctors.setCtorArgs(
      tInfiniteBools,
      Arrays.asList(
        EfVar.arg("head", 0, tBool), 
        EfVar.arg("tail", 1, tInfiniteBools)));
    
    tCons = new EfType.SimpleType("Cons", Collections.singletonList("T"));
    tListGeneric = tCons.getGenericsDeclr().get(0);
    tEmpty = new EfType.SimpleType("Empty", Collections.emptyList());
    tList = (EfType.DisjunctiveType) EfType.disjunction(tCons, tEmpty);
    ctors.setCtorArgs(
      tCons,
      Arrays.asList(
        EfVar.arg("head", 0, tListGeneric),
        EfVar.arg("tail", 1, tList)));
    ctors.setCtorArgs(tEmpty, Collections.emptyList());

    Function<EfType.GenericType, EfType> boolReification = Functions.forMap(Collections.singletonMap(tListGeneric, tBool))::apply;
    tBoolsList = tList.reify(boolReification);
    tBoolCons = this.tCons.reify(boolReification);
  }

  @Test
  public void listPattern() {
    PPossibility boolsPossibility = PPossibility.from(tBoolsList, ctors);
    PAlternative firstIsTrue = boolCons(mTrue(), any()).build(ctors);
    PAlternative secondIsTrue = boolCons(
      any(),
      boolCons(
        mTrue(),
        any())
    ).build(ctors);

    PPossibility result = boolsPossibility.minus(firstIsTrue);
    disjunctionV(
      singleV(tBoolCons,
        singleV(tFalse),
        unforcedV()
      ),
      unforcedV()
    ).validate(result);
    assertNotNull(result);
    
    PPossibility second = result.minus(secondIsTrue);
    fail("validate somehow that it's [False, False, _]: " + second);
  }

  private Builder boolCons(Builder head, Builder tail) {
    return simple(
      tBoolCons,
      head,
      tail
    );
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
    PPossibility.from(tInfiniteBools, ctors);
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
    String desc = type.toString();
    if (args.length != 0) {
      StringBuilder sb = new StringBuilder(desc).append('(');
      desc = Joiner.on(", ").appendTo(sb, args).append(')').toString();
    }
    return new Validator(
      desc,
      forced(actual -> {
        assertThat(actual, instanceOf(PPossibility.Simple.class));
        PPossibility.Simple actualSimple = (PPossibility.Simple) actual;
        assertThat(actualSimple.typedAndArgs().type(), equalTo(type));
        actualSimple.typedAndArgs().consume(
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

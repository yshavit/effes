package com.yuvalshavit.effes.compile.pmatch;

import static com.yuvalshavit.effes.compile.pmatch.PAlternative.*;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.function.Function;

import org.testng.annotations.Test;

import com.google.common.base.Functions;
import com.yuvalshavit.effes.compile.CtorRegistry;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.effes.compile.node.EfVar;

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
    fail("validate somehow that it's [False, _]: " + result);
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
  
  
}

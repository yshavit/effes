package com.yuvalshavit.effes.compile.pmatch;

import static com.yuvalshavit.effes.compile.pmatch.PAlternative.*;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
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
  private final EfType.GenericType tListGeneric;
  
  private final CtorRegistry ctors;

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
  }

  @Test
  public void listPattern() {
    Map<EfType.GenericType, EfType> tIsBool = Collections.<EfType.GenericType, EfType>singletonMap(tListGeneric, tBool);
    Function<EfType.GenericType, EfType> reification = Functions.forMap(tIsBool)::apply;
    EfType boolsList = tList.reify(reification);
    PPossibility boolsPossibility = PPossibility.from(boolsList, ctors);
    EfType.SimpleType boolCon = this.tCons.reify(reification);
    PAlternative firstIsTrue = simple(boolCon, simple(tTrue), any()).build(ctors);

    PPossibility result = boolsPossibility.minus(firstIsTrue);
    fail("validate somehow that it's [False, _]: " + result);
  }
  
  @Test
  public void trueNoArgs() {
    assertNotNull(simple(tTrue).build(ctors));
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

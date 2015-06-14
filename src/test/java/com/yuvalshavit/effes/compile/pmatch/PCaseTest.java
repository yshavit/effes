package com.yuvalshavit.effes.compile.pmatch;

import java.util.Arrays;
import java.util.Collections;

import org.testng.annotations.Test;

import com.yuvalshavit.effes.compile.CtorRegistry;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.effes.compile.node.EfVar;

public class PCaseTest {
  private final EfType.SimpleType tTrue;
  private final EfType.SimpleType tFalse;
  private final EfType.DisjunctiveType tBool;
  private final EfType.SimpleType tInfiniteBools;
  private final CtorRegistry ctors;

  public PCaseTest() {
    tTrue = new EfType.SimpleType("True", Collections.emptyList());
    tFalse = new EfType.SimpleType("False", Collections.emptyList());
    tBool = (EfType.DisjunctiveType) EfType.disjunction(tTrue, tFalse);
    tInfiniteBools = new EfType.SimpleType("InfiniteInts", Collections.emptyList());
    
    ctors = new CtorRegistry();
    ctors.setCtorArgs(tTrue, Collections.emptyList());
    ctors.setCtorArgs( tInfiniteBools,
      Arrays.asList(
        EfVar.arg("head", 0, tBool), 
        EfVar.arg("tail", 1, tInfiniteBools)));
  }

  @Test
  public void test() {
    
  }

  @Test
  public void testCaseWhereSimpleTypeIsInfinitelyRecursiveWithNoBaseCase() {
    PPossibility possibility = PPossibility.from(tInfiniteBools, ctors);
    PPossibility.Simple simple = (PPossibility.Simple) possibility;
    simple.force(0);
    // ie, Integers = Integers(value: Int, next: Integers)
    // We should be able to use this in a pattern, so long as we grab all using _
    // Note that there is no disjunction here, so the laziness MUST be at the Simple level!
    throw new UnsupportedOperationException(); // TODO
  }
  
}

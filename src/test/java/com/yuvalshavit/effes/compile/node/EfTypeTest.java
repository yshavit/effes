package com.yuvalshavit.effes.compile.node;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.testng.Assert.assertEquals;

public final class EfTypeTest {

  final EfType simpleA = new EfType.SimpleType("Alpha", Collections.emptyList());
  final EfType simpleB = new EfType.SimpleType("Bravo", Collections.emptyList());
  final EfType simpleC = new EfType.SimpleType("Charlie", Collections.emptyList());
  final EfType simpleD = new EfType.SimpleType("Delta", Collections.emptyList());

  @Test
  public void simpleOrdering() {
    EfType dis = EfType.disjunction(simpleA, simpleB);
    List<EfType> unordered = Arrays.asList(simpleA, simpleB, simpleB, simpleC, EfType.UNKNOWN, EfType.UNKNOWN, null, null, dis);
    Collections.shuffle(unordered);
    Collections.sort(unordered, EfType.comparator);

    List<EfType> expected = Arrays.asList(null, null, EfType.UNKNOWN, EfType.UNKNOWN, simpleA, simpleB, simpleB, simpleC, dis);
    assertEquals(unordered, expected);
  }

  @Test
  public void disjunctivesSameUntilDifferentLengths() {
    EfType d1 = EfType.disjunction(simpleA, simpleB, simpleC);
    EfType d2 = EfType.disjunction(simpleA, simpleB);
    assertEquals(cmp(d1, d2), 1);
    assertEquals(cmp(d2, d1), -1);
    assertEquals(cmp(d1, d1), 0);
    assertEquals(cmp(d2, d2), 0);
  }

  @Test
  public void disjunctivesDifferentWithDifferentLengths() {
    EfType d1 = EfType.disjunction(simpleA, simpleB, EfType.UNKNOWN);
    EfType d2 = EfType.disjunction(simpleA, simpleC);
    // if we only compared lengths, d2 would be first. But we compare by elements (in order) first, so d1 is first
    assertEquals(cmp(d1, d2), -3);
    assertEquals(cmp(d2, d1), 3);
    assertEquals(cmp(d1, d1), 0);
    assertEquals(cmp(d2, d2), 0);
  }

  @Test
  public void disjunctivesRecursive() {
    // TODO This test is actually bogus, since disjunctive types flatten themselves out.
    // That means there's no there's no recursion by the time we get to it here; d1 is (UNKNOWN | A | B ), not
    // (UNKNOWN | (A|B)).
    // This will change once we get conjunctive types.
    EfType innerA = EfType.disjunction(simpleA, simpleB);
    EfType innerB = EfType.disjunction(simpleA, simpleC);

    // innerA < innerB, so d1 < d2
    EfType d1 = EfType.disjunction(simpleD, innerA);
    EfType d2 = EfType.disjunction(simpleD, innerB);

    assertEquals(cmp(d1, d2), -1);
    assertEquals(cmp(d2, d1), 1);
    assertEquals(cmp(d1, d1), 0);
    assertEquals(cmp(d2, d2), 0);
  }

  @Test
  public void unknownDisjunctionIsUnknown() {
    Assert.assertEquals(EfType.disjunction(simpleA, simpleB, EfType.UNKNOWN), EfType.UNKNOWN);
  }

  @Test
  public void voidDisjunctionIsVoid() {
    Assert.assertEquals(EfType.disjunction(simpleA, simpleB, EfType.VOID), EfType.VOID);
  }

  private static int cmp(EfType t1, EfType t2) {
    return EfType.comparator.compare(t1, t2);
  }
}

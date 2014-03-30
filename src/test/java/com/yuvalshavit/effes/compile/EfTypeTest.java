package com.yuvalshavit.effes.compile;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.yuvalshavit.effes.compile.EfType.UNKNOWN;
import static org.testng.Assert.assertEquals;

public final class EfTypeTest {

  final EfType simpleA = new EfType.SimpleType("Alpha");
  final EfType simpleB = new EfType.SimpleType("Bravo");
  final EfType simpleC = new EfType.SimpleType("Charlie");

  @Test
  public void simpleOrdering() {
    EfType dis = disjunctive(simpleA, simpleB);
    List<EfType> unordered = Arrays.asList(simpleA, simpleB, simpleB, simpleC, UNKNOWN, UNKNOWN, null, null, dis);
    Collections.shuffle(unordered);
    Collections.sort(unordered, EfType.comparator);

    List<EfType> expected = Arrays.asList(null, null, UNKNOWN, UNKNOWN, simpleA, simpleB, simpleB, simpleC, dis);
    assertEquals(unordered, expected);
  }

  @Test
  public void disjunctivesSameUntilDifferentLengths() {
    EfType d1 = disjunctive(simpleA, simpleB, simpleC);
    EfType d2 = disjunctive(simpleA, simpleB);
    assertEquals(cmp(d1, d2), 1);
    assertEquals(cmp(d2, d1), -1);
    assertEquals(cmp(d1, d1), 0);
    assertEquals(cmp(d2, d2), 0);
  }

  @Test
  public void disjunctivesDifferentWithDifferentLengths() {
    EfType d1 = disjunctive(simpleA, simpleB, UNKNOWN);
    EfType d2 = disjunctive(simpleA, simpleC);
    // if we only compared lengths, d2 would be first. But we compare by elements (in order) first, so d1 is first
    assertEquals(cmp(d1, d2), -1);
    assertEquals(cmp(d2, d1), 1);
    assertEquals(cmp(d1, d1), 0);
    assertEquals(cmp(d2, d2), 0);
  }

  @Test
  public void disjunctivesRecursive() {
    // TODO This test is actually bogus, since disjunctive types flatten themselves out.
    // That means there's no there's no recursion by the time we get to it here; d1 is (UNKNOWN | A | B ), not
    // (UNKNOWN | (A|B)).
    // This will change once we get conjunctive types.
    EfType innerA = disjunctive(simpleA, simpleB);
    EfType innerB = disjunctive(simpleA, simpleC);

    // innerA < innerB, so d1 < d2
    EfType d1 = disjunctive(UNKNOWN, innerA);
    EfType d2 = disjunctive(UNKNOWN, innerB);

    assertEquals(cmp(d1, d2), -1);
    assertEquals(cmp(d2, d1), 1);
    assertEquals(cmp(d1, d1), 0);
    assertEquals(cmp(d2, d2), 0);
  }

  private static EfType disjunctive(EfType... options) {
    return new EfType.DisjunctiveType(ImmutableList.copyOf(options));
  }

  private static int cmp(EfType t1, EfType t2) {
    return EfType.comparator.compare(t1, t2);
  }
}

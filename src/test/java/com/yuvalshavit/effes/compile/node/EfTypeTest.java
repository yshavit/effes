package com.yuvalshavit.effes.compile.node;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.yuvalshavit.effes.compile.node.EfType.disjunction;
import static com.yuvalshavit.effes.compile.node.Reifications.onlyGenericOf;
import static com.yuvalshavit.effes.compile.node.Reifications.reifyOnlyGenericOf;
import static com.yuvalshavit.util.EfTestHelper.safely;
import static java.util.Collections.singletonList;
import static org.testng.Assert.assertEquals;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public final class EfTypeTest {

  final EfType simpleA = simpleTypeNamed("Alpha");
  final EfType simpleB = simpleTypeNamed("Bravo");
  final EfType simpleC = simpleTypeNamed("Charlie");
  final EfType simpleD = simpleTypeNamed("Delta");

  @Test
  public void simpleOrdering() {
    EfType dis = disjunction(simpleA, simpleB);
    List<EfType> unordered = Arrays.asList(simpleA, simpleB, simpleB, simpleC, EfType.UNKNOWN, EfType.UNKNOWN, null, null, dis);
    Collections.shuffle(unordered);
    Collections.sort(unordered, EfType.comparator);

    List<EfType> expected = Arrays.asList(null, null, EfType.UNKNOWN, EfType.UNKNOWN, simpleA, simpleB, simpleB, simpleC, dis);
    assertEquals(unordered, expected);
  }

  @Test
  public void isjunctivesSameUntilDifferentLengths() {
    EfType d1 = disjunction(simpleA, simpleB, simpleC);
    EfType d2 = disjunction(simpleA, simpleB);
    assertEquals(cmp(d1, d2), 1);
    assertEquals(cmp(d2, d1), -1);
    assertEquals(cmp(d1, d1), 0);
    assertEquals(cmp(d2, d2), 0);
  }

  @Test
  public void disjunctivesDifferentWithDifferentLengths() {
    EfType d1 = disjunction(simpleA, simpleB, EfType.UNKNOWN);
    EfType d2 = disjunction(simpleA, simpleC);
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
    EfType innerA = disjunction(simpleA, simpleB);
    EfType innerB = disjunction(simpleA, simpleC);

    // innerA < innerB, so d1 < d2
    EfType d1 = disjunction(simpleD, innerA);
    EfType d2 = disjunction(simpleD, innerB);

    assertEquals(cmp(d1, d2), -1);
    assertEquals(cmp(d2, d1), 1);
    assertEquals(cmp(d1, d1), 0);
    assertEquals(cmp(d2, d2), 0);
  }

  @Test
  public void unknownDisjunctionIsUnknown() {
    Assert.assertEquals(disjunction(simpleA, simpleB, EfType.UNKNOWN), EfType.UNKNOWN);
  }

  @Test
  public void voidDisjunctionIsVoid() {
    Assert.assertEquals(disjunction(simpleA, simpleB, EfType.VOID), EfType.VOID);
  }

  @Test
  public void reifyToCtorsInSimpleCase() {
    EfType.SimpleType genericBox = new EfType.SimpleType("Box", singletonList("T"));
    genericBox.setCtorArgs(singletonList(new CtorArg(0, "value", onlyGenericOf(genericBox))));
    
    EfType aOrB = disjunction(simpleA, simpleB);
    EfType.SimpleType boxOfAOrB = reifyOnlyGenericOf(genericBox).to(aOrB);
    assertEquals(Iterables.getOnlyElement(boxOfAOrB.getParams()), aOrB);

    EfType.SimpleType boxOfA = genericBox.withCtorArgs(simpleA);
    assertEquals(Iterables.getOnlyElement(boxOfAOrB.getParams()), aOrB);
    assertEquals(Iterables.getOnlyElement(boxOfA.getArgs()).type(), simpleA);
  }

  @Test
  public void reifyToCtorsWhenGenericIsInDisjunction() {
    EfType.SimpleType genericMaybe = new EfType.SimpleType("Maybe", singletonList("T"));
    EfType.GenericType genericArg = onlyGenericOf(genericMaybe);
    genericMaybe.setCtorArgs(singletonList(new CtorArg(0, "value", disjunction(genericArg, simpleA, simpleB))));

    EfType.SimpleType maybeB = reifyOnlyGenericOf(genericMaybe).to(disjunction(simpleB, simpleC));
    assertEquals(Iterables.getOnlyElement(maybeB.getParams()), disjunction(simpleB, simpleC));
    assertEquals(Iterables.getOnlyElement(maybeB.getArgs()).type(), disjunction(simpleA, simpleB, simpleC));

    EfType.SimpleType actuallyB = maybeB.withCtorArgs(simpleB);
    assertEquals(Iterables.getOnlyElement(actuallyB.getParams()), disjunction(simpleB, simpleC));
    assertEquals(Iterables.getOnlyElement(actuallyB.getArgs()).type(), simpleB);
  }

  @Test
  public void reifyToCtorsWhenGenericIsInNesting() {
    // Inner[J](innerVal: J)
    EfType.SimpleType innerGeneric = new EfType.SimpleType("Inner", singletonList("J"));
    innerGeneric.setCtorArgs(singletonList(new CtorArg(0, "innerVal", onlyGenericOf(innerGeneric))));
    // Outer[T](outerVal: Inner[T])
    EfType.SimpleType outerGeneric = new EfType.SimpleType("Outer", singletonList("T"));
    outerGeneric.setCtorArgs(singletonList(new CtorArg(0, "outerVal", reifyOnlyGenericOf(innerGeneric).to(onlyGenericOf(outerGeneric)))));

    EfType.SimpleType outerAOrB = reifyOnlyGenericOf(outerGeneric).to(disjunction(simpleA, simpleB))
      .withCtorArgs(simpleA);
    assertEquals(Iterables.getOnlyElement(outerAOrB.getParams()), disjunction(simpleA, simpleB));
    assertEquals(Iterables.getOnlyElement(outerAOrB.getArgs()).type(), simpleA);
  }

  @Test
  public void constrainUnionWhenSameType() {
    EfType.SimpleType union = createUnionType("T", "E");
    EfType.SimpleType reified = Reifications.reify(union)
      .with("T", disjunction(simpleA, simpleB))
      .with("E", disjunction(simpleB, simpleC))
      .get()
      .withCtorArgs(simpleB);
    assertEquals(Lists.transform(reified.getArgs(), CtorArg::type), singletonList(simpleB));
    assertEquals(reified.getParams(), Arrays.asList(disjunction(simpleA, simpleB), disjunction(simpleB, simpleC)));
  }

  @Test
  public void constrainUnionWhenDifferentTypes() {
    EfType.SimpleType union = createUnionType("T", "E");
    EfType.SimpleType reified = Reifications.reify(union)
      .with("T", disjunction(simpleA, simpleB))
      .with("E", disjunction(simpleB, simpleC))
      .get()
      .withCtorArgs(simpleA);
    assertEquals(Lists.transform(reified.getArgs(), CtorArg::type), singletonList(simpleA));
    assertEquals(reified.getParams(), Arrays.asList(disjunction(simpleA, simpleB), disjunction(simpleB, simpleC)));
  }

  @Test
  public void constrainUnionWhenMutuallyExclusiveType() {
    EfType.SimpleType union = createUnionType("T", "E");
    EfType.SimpleType reified = Reifications.reify(union)
      .with("T", disjunction(simpleA, simpleB))
      .with("E", disjunction(simpleC, simpleD))
      .get()
      .withCtorArgs(simpleB);
    assertEquals(Lists.transform(reified.getArgs(), CtorArg::type), singletonList(simpleB));
    assertEquals(reified.getParams(), Arrays.asList(disjunction(simpleA, simpleB), disjunction(simpleC, simpleD)));
  }
  
  @Test
  public void reificationShadowsDisjunction() {
    EfType.SimpleType genericMaybe = new EfType.SimpleType("Maybe", singletonList("T"));
    EfType.GenericType genericArg = onlyGenericOf(genericMaybe);
    genericMaybe.setCtorArgs(singletonList(new CtorArg(0, "value", disjunction(genericArg, simpleA))));

    EfType.SimpleType maybeA = reifyOnlyGenericOf(genericMaybe).to(simpleA);
    assertEquals(Iterables.getOnlyElement(maybeA.getParams()), simpleA);
    assertEquals(Iterables.getOnlyElement(maybeA.getArgs()).type(), simpleA);
  }

  @Test
  public void withCtorArgsWithInapplicableArg() {
    EfType.SimpleType box = safely(() -> {
      EfType.SimpleType boxType = new EfType.SimpleType("Box", singletonList("T"));
      EfType aOrB = disjunction(simpleA, simpleB);
      boxType.setCtorArgs(singletonList(new CtorArg(0, "value", aOrB)));
      EfType.SimpleType boxOfAOrB = reifyOnlyGenericOf(boxType).to(aOrB);
      assertEquals(Iterables.getOnlyElement(boxOfAOrB.getParams()), aOrB);
      return boxOfAOrB;
    });

    EfType.SimpleType weirdBox = box.withCtorArgs(simpleC); // not a or b!
    assertEquals(weirdBox.getArgs(), Collections.singletonList(new CtorArg(0, "value", simpleC)));
    assertEquals(weirdBox.getParams(), Collections.singletonList(EfType.disjunction(simpleA, simpleB)));
  }

  private static int cmp(EfType t1, EfType t2) {
    return EfType.comparator.compare(t1, t2);
  }

  private static EfType.SimpleType simpleTypeNamed(String name) {
    EfType.SimpleType type = new EfType.SimpleType(name, Collections.emptyList());
    type.setCtorArgs(Collections.emptyList());
    return type;
  }

  private EfType.SimpleType createUnionType(String first, String second, String... rest) {
    List<String> all = new ArrayList<>(rest.length + 2);
    all.addAll(Arrays.asList(first, second));
    all.addAll(Arrays.asList(rest));
    if (all.stream().distinct().count() != all.size()) {
      throw new IllegalArgumentException("must provide all distinct names: " + all);
    }
    // type Union[T, E] (value: T | E)
    EfType.SimpleType union = new EfType.SimpleType("Union", all);
    List<EfType.GenericType> allGenerics = all.stream().map(name -> Reifications.genericByName(union, name)).collect(Collectors.toList());
    union.setCtorArgs(singletonList(new CtorArg(0, "value", disjunction(allGenerics))));
    return union;
  }
}

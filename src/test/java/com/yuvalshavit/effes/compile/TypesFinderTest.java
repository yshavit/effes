package com.yuvalshavit.effes.compile;

import com.google.common.collect.Sets;
import com.yuvalshavit.effes.TUtils;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

public final class TypesFinderTest {
  @Test
  public void findDataTypes() throws IOException {
    EffesParser parser = ParserUtils.createParser(
      "data type True",
      "data type False");
    TypeRegistry registry = new TypeRegistry(CompileErrors.throwing);
    new TypesFinder(registry, null).accept(parser.compilationUnit());
    assertEquals(registry.getAllSimpleTypeNames(), Sets.newHashSet("True", "False"));
  }

  @Test
  public void duplicateTypes() {
    EffesParser parser = ParserUtils.createParser(
      "data type True",
      "data type True"
    );
    TUtils.expectErrors(errs -> {
      TypeRegistry registry = new TypeRegistry(errs);
      new TypesFinder(registry, null).accept(parser.compilationUnit());
    });
  }

  @Test
  public void dataTypeHasSimpleArgs() {
    EffesParser parser = ParserUtils.createParser(
      "data type One",
      "data type Another",
      "data type Two(only: One, okay: Another)"
    );
    TypeRegistry registry = new TypeRegistry(CompileErrors.throwing);
    new TypesFinder(registry, null).accept(parser.compilationUnit());

    assertEquals(registry.getAllSimpleTypeNames(), Sets.newHashSet("One", "Two", "Another"));
    EfType.SimpleType one = registry.getSimpleType("One");
    EfType.SimpleType another = registry.getSimpleType("Another");
    EfType.SimpleType two = registry.getSimpleType("Two");
    assertNotNull(two);

    assertEquals(two.getArgs(), Arrays.asList(EfVar.arg("only", 0, one), EfVar.arg("okay", 1, another)));
    assertEquals(two.toString(), "Two");
  }

  @Test
  public void dataTypeHasDisjunctionArg() {
    EffesParser parser = ParserUtils.createParser(
      "data type Cat",
      "data type Dog",
      "data type Pet(species: Cat | Dog)"
    );
    TypeRegistry registry = new TypeRegistry(CompileErrors.throwing);
    new TypesFinder(registry, null).accept(parser.compilationUnit());

    assertEquals(registry.getAllSimpleTypeNames(), Sets.newHashSet("Cat", "Dog", "Pet"));
    EfType.SimpleType cat = registry.getSimpleType("Cat");
    EfType.SimpleType dog = registry.getSimpleType("Dog");
    EfType.SimpleType pet = registry.getSimpleType("Pet");
    assertNotNull(pet);

    EfType catOrdog = EfType.disjunction(cat, dog);
    assertEquals(pet.getArgs(), Arrays.asList(EfVar.arg("species", 0, catOrdog)));
    assertEquals(pet.toString(), "Pet");
  }

  @Test
  public void infiniteRecursion() {
    EffesParser parser = ParserUtils.createParser(
      "data type Infinity(forever: Infinity)"
    );
    TypeRegistry registry = new TypeRegistry(CompileErrors.throwing);
    new TypesFinder(registry, null).accept(parser.compilationUnit());

    assertEquals(registry.getAllSimpleTypeNames(), Sets.newHashSet("Infinity"));
    EfType.SimpleType infinity = registry.getSimpleType("Infinity");
    assertNotNull(infinity);

    assertEquals(infinity.getArgs(), Arrays.asList(EfVar.arg("forever", 0, infinity)));
  }

  @Test
  public void normalRecursion() {
    EffesParser parser = ParserUtils.createParser(
      "data type Elem",
      "data type Empty",
      "data type Cons(head: Elem, tail: Cons | Empty)"
    );
    TypeRegistry registry = new TypeRegistry(CompileErrors.throwing);
    new TypesFinder(registry, null).accept(parser.compilationUnit());

    assertEquals(registry.getAllSimpleTypeNames(), Sets.newHashSet("Elem", "Empty", "Cons"));
    EfType.SimpleType cons = registry.getSimpleType("Cons");
    assertNotNull(cons);

    EfType.SimpleType elem = registry.getSimpleType("Elem");
    EfType.SimpleType empty = registry.getSimpleType("Empty");
    EfType tailType = EfType.disjunction(cons, empty);
    assertEquals(cons.getArgs(), Arrays.asList(EfVar.arg("head", 0, elem), EfVar.arg("tail", 1, tailType)));
    assertEquals(cons.toString(), "Cons");
  }

  @Test
  public void simpleAlias() {
    CompileErrors errs = new CompileErrors();
    TypeRegistry registry = findTypes(
      errs,
      "data type True",
      "data type False",
      "type Boolean = True | False"
    );
    assertFalse(errs.hasErrors());
    assertDisjunctiveType(registry, "Boolean", "True", "False");
  }

  @Test
  public void aliasReferencesPreviousAlias() {
    CompileErrors errs = new CompileErrors();
    TypeRegistry registry = findTypes(
      errs,
      "data type True",
      "data type False",
      "data type Void",
      "type BooleanA = True | False",
      "type BooleanB = BooleanA | Void"
    );
    assertFalse(errs.hasErrors());
    assertDisjunctiveType(registry, "BooleanA", "True", "False");
    assertDisjunctiveType(registry, "BooleanB", "True", "False", "Void");
  }

  @Test
  public void aliasReferencesUpcomingAlias() {
    CompileErrors errs = new CompileErrors();
    TypeRegistry registry = findTypes(
      errs,
      "data type True",
      "data type False",
      "data type Void",
      "type BooleanA = BooleanB | Void",
      "type BooleanB = True | False"
    );
    assertFalse(errs.hasErrors());
    assertDisjunctiveType(registry, "BooleanA", "True", "False", "Void");
    assertDisjunctiveType(registry, "BooleanB", "True", "False");
  }

  @Test
  public void aliasToSingleType() {
    CompileErrors errs = new CompileErrors();
    TypeRegistry registry = findTypes(
      errs,
      "data type True",
      "type AlsoTrue = True"
    );
    assertFalse(errs.hasErrors());
    String aliasName = "AlsoTrue";
    String aliasResolution = "True";
    assertSimpleAlias(registry, aliasName, aliasResolution);
  }

  @Test
  public void aliasRecursionOnSelf() {
    CompileErrors errs = new CompileErrors();
    TypeRegistry registry = findTypes(
      errs,
      "data type True",
      "type Boolean = True | Boolean"
    );
    assertEquals(errs.getErrors().toString(), "[cyclic type alias definition (at line 2, column 6: Boolean)]");
    assertSimpleAlias(registry, "Boolean", "True");
  }

  @Test
  public void aliasRecursionIndirect() {
    CompileErrors errs = new CompileErrors();
    TypeRegistry registry = findTypes(
      errs,
      "data type True",
      "type AlsoTrue = True | Boolean",
      "type Boolean = True | AlsoTrue"
    );
    assertEquals(errs.getErrors().toString(), "[cyclic type alias definition (at line 2, column 6: AlsoTrue)]");
    assertSimpleAlias(registry, "Boolean", "True");
    assertSimpleAlias(registry, "AlsoTrue", "True");
  }

  @Test
  public void aliasToUnknownType() {
    CompileErrors errs = new CompileErrors();
    TypeRegistry registry = findTypes(
      errs,
      "data type True",
      "type Boolean = True | False"
    );
    assertEquals(errs.getErrors().toString(), "[unknown type (at line 2, column 23: False)]");
    assertSimpleAlias(registry, "Boolean", "True");
  }

  private void assertSimpleAlias(TypeRegistry registry, String aliasName, String aliasResolution) {
    assertNull(registry.getSimpleType(aliasName));
    EfType alsoTrue = registry.getType(aliasName);
    assertNotNull(alsoTrue);
    assertEquals(registry.getSimpleType(aliasResolution), alsoTrue);
  }

  private void assertDisjunctiveType(TypeRegistry registry,
                                     String aliasName,
                                     String firstAlternative,
                                     String secondAlternative,
                                     String... remainingAlternatives) {
    List<String> alternativeNames = new ArrayList<>();
    alternativeNames.add(firstAlternative);
    alternativeNames.add(secondAlternative);
    Collections.addAll(alternativeNames, remainingAlternatives);
    EfType aliasTarget = registry.getType(aliasName);
    assertNotNull(aliasTarget);
    assertEquals(aliasTarget.getClass(), EfType.DisjunctiveType.class);
    EfType.DisjunctiveType aliasTargetDisjunction = (EfType.DisjunctiveType) aliasTarget;
    Iterator<? extends EfType> alternatives = alternativeNames.stream().map(registry::getSimpleType).iterator();
    assertEquals(aliasTargetDisjunction.getAlternatives(), Sets.newHashSet(alternatives));
  }

  private static TypeRegistry findTypes(CompileErrors errs, String... source) {
    EffesParser parser = ParserUtils.createParser(source);
    TypeRegistry registry = new TypeRegistry(errs);
    TypesFinder typesFinder = new TypesFinder(registry, errs) {
      @Override
      <K extends Comparable<? super K>, V> Map<K, V> createMap() {
        return new TreeMap<>();
      }
    };
    typesFinder.accept(parser.compilationUnit());
    return registry;
  }
}

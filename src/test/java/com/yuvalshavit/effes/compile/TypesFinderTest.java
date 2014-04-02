package com.yuvalshavit.effes.compile;

import com.google.common.collect.Sets;
import com.yuvalshavit.effes.TUtils;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

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
}

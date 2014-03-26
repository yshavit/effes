package com.yuvalshavit.effes.compile;

import com.google.common.collect.Sets;
import com.yuvalshavit.effes.TUtils;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;
import org.testng.annotations.Test;

import static com.yuvalshavit.util.AssertException.assertException;
import static org.testng.Assert.assertEquals;

public final class MethodsFinderTest {
  @Test
  public void findMethodNames() {
    TypeRegistry types = TUtils.typeRegistry("True");
    EffesParser parser = ParserUtils.createParser(
      "def id True -> True: return True",
      "def ego True -> True: return True");
    MethodsRegistry<EffesParser.InlinableBlockContext> methodsRegistry = new MethodsRegistry<>();
    TUtils.expectNoErrors(errs -> findMethods(types, methodsRegistry, parser, errs));
    assertEquals(methodsRegistry.getAllTopLevelMethodNames(), Sets.newHashSet("id", "ego"));
  }

  @Test
  public void unknownArgType() {
    TypeRegistry types = TUtils.typeRegistry("True");
    EffesParser parser = ParserUtils.createParser(
      "def bogus False -> True: return True"
    );
    MethodsRegistry<EffesParser.InlinableBlockContext> methodsRegistry = new MethodsRegistry<>();
    TUtils.expectErrors(errs -> findMethods(types, methodsRegistry, parser, errs));
  }

  @Test
  public void unknownResultType() {
    TypeRegistry types = TUtils.typeRegistry("True");
    EffesParser parser = ParserUtils.createParser(
      "def not True -> False: return False"
    );
    MethodsRegistry<EffesParser.InlinableBlockContext> methodsRegistry = new MethodsRegistry<>();
    TUtils.expectErrors(errs -> findMethods(types, methodsRegistry, parser, errs));
  }

  @Test
  public void overloadedMethods() {
    TypeRegistry types = TUtils.typeRegistry("True", "False");
    EffesParser parser = ParserUtils.createParser(
      "def not True -> False: return False",
      "def not False -> True: return True"
    );
    MethodsRegistry<EffesParser.InlinableBlockContext> methodsRegistry = new MethodsRegistry<>();
    TUtils.expectErrors(errs -> findMethods(types, methodsRegistry, parser, errs));
  }

  @Test
  public void identicalMethodSignatures() {
    TypeRegistry types = TUtils.typeRegistry("True", "False");
    EffesParser parser = ParserUtils.createParser(
      "def not True -> False: return False",
      "def not True -> True: return True"
    );
    MethodsRegistry<EffesParser.InlinableBlockContext> methodsRegistry = new MethodsRegistry<>();
    TUtils.expectErrors(errs -> findMethods(types, methodsRegistry, parser, errs));
  }

  private static void findMethods(TypeRegistry types,
                                  MethodsRegistry<EffesParser.InlinableBlockContext> methodsRegistry,
                                  EffesParser parser,
                                  CompileErrors errs) {
    new MethodsFinder(types, methodsRegistry, errs).accept(parser.compilationUnit());
  }
}

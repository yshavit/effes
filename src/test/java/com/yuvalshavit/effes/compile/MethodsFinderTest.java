package com.yuvalshavit.effes.compile;

import com.google.common.collect.Sets;
import com.yuvalshavit.effes.TUtils;
import com.yuvalshavit.effes.compile.node.CompileErrors;
import com.yuvalshavit.effes.compile.node.MethodId;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public final class MethodsFinderTest {
  @Test
  public void findMethodNames() {
    TypeRegistry types = TUtils.typeRegistry("True");
    EffesParser parser = ParserUtils.createParser(
      "id True -> True: return True",
      "ego True -> True: return True");
    MethodsRegistry<EffesParser.InlinableBlockContext> methodsRegistry = new MethodsRegistry<>();
    TUtils.expectNoErrors(errs -> findMethods(types, methodsRegistry, parser, errs));
    assertEquals(methodsRegistry.getMethodIds(), Sets.newHashSet(MethodId.topLevel("id"), MethodId.topLevel("ego")));
  }

  @Test
  public void unknownArgType() {
    TypeRegistry types = TUtils.typeRegistry("True");
    EffesParser parser = ParserUtils.createParser(
      "bogus False -> True: return True"
    );
    MethodsRegistry<EffesParser.InlinableBlockContext> methodsRegistry = new MethodsRegistry<>();
    TUtils.expectErrors(errs -> findMethods(types, methodsRegistry, parser, errs));
  }

  @Test
  public void unknownResultType() {
    TypeRegistry types = TUtils.typeRegistry("True");
    EffesParser parser = ParserUtils.createParser(
      "not True -> False: return False"
    );
    MethodsRegistry<EffesParser.InlinableBlockContext> methodsRegistry = new MethodsRegistry<>();
    TUtils.expectErrors(errs -> findMethods(types, methodsRegistry, parser, errs));
  }

  @Test
  public void overloadedMethods() {
    TypeRegistry types = TUtils.typeRegistry("True", "False");
    EffesParser parser = ParserUtils.createParser(
      "not True -> False: return False",
      "not False -> True: return True"
    );
    MethodsRegistry<EffesParser.InlinableBlockContext> methodsRegistry = new MethodsRegistry<>();
    TUtils.expectErrors(errs -> findMethods(types, methodsRegistry, parser, errs));
  }

  @Test
  public void identicalMethodSignatures() {
    TypeRegistry types = TUtils.typeRegistry("True", "False");
    EffesParser parser = ParserUtils.createParser(
      "not True -> False: return False",
      "not True -> True: return True"
    );
    MethodsRegistry<EffesParser.InlinableBlockContext> methodsRegistry = new MethodsRegistry<>();
    TUtils.expectErrors(errs -> findMethods(types, methodsRegistry, parser, errs));
  }

  private static void findMethods(TypeRegistry types,
                                  MethodsRegistry<EffesParser.InlinableBlockContext> methodsRegistry,
                                  EffesParser parser,
                                  CompileErrors errs) {
    MethodsRegistry<?> fakedBuiltinMethods = new MethodsRegistry<>();
    new MethodsFinder(types, fakedBuiltinMethods, methodsRegistry, errs)
      .accept(SourcesFactory.withoutBuiltins(parser));
  }
}

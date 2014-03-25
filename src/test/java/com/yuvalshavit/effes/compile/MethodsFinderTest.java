package com.yuvalshavit.effes.compile;

import com.google.common.collect.Sets;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;
import org.testng.annotations.Test;

import static com.yuvalshavit.util.AssertException.assertException;
import static org.testng.Assert.assertEquals;

public final class MethodsFinderTest {
  @Test
  public void findMethodNames() {
    TypeRegistry types = typeRegisry("True");
    EffesParser parser = ParserUtils.createParser(
      "def id True -> True: return True",
      "def ego True -> True: return True");
    MethodsRegistry methodsRegistry = new MethodsRegistry();
    new MethodsFinder(types, methodsRegistry).accept(parser);
    assertEquals(methodsRegistry.getAllTopLevelMethodNames(), Sets.newHashSet("id", "ego"));
  }

  @Test
  public void unknownArgType() {
    TypeRegistry types = typeRegisry("True");
    EffesParser parser = ParserUtils.createParser(
      "def bogus False -> True: return True"
    );
    MethodsRegistry methodsRegistry = new MethodsRegistry();
    assertException(MethodRegistrationException.class, () -> new MethodsFinder(types, methodsRegistry).accept(parser));
  }

  @Test
  public void unknownResultType() {
    TypeRegistry types = typeRegisry("True");
    EffesParser parser = ParserUtils.createParser(
      "def not True -> False: return False"
    );
    MethodsRegistry methodsRegistry = new MethodsRegistry();
    assertException(MethodRegistrationException.class, () -> new MethodsFinder(types, methodsRegistry).accept(parser));
  }

  @Test
  public void overloadedMethods() {
    TypeRegistry types = typeRegisry("True", "False");
    EffesParser parser = ParserUtils.createParser(
      "def not True -> False: return False",
      "def not False -> True: return True"
    );
    MethodsRegistry methodsRegistry = new MethodsRegistry();
    assertException(MethodRegistrationException.class, () -> new MethodsFinder(types, methodsRegistry).accept(parser));
  }

  @Test
  public void identicalMethodSignatures() {
    TypeRegistry types = typeRegisry("True", "False");
    EffesParser parser = ParserUtils.createParser(
      "def not True -> False: return False",
      "def not True -> True: return True"
    );
    MethodsRegistry methodsRegistry = new MethodsRegistry();
    assertException(MethodRegistrationException.class, () -> new MethodsFinder(types, methodsRegistry).accept(parser));
  }

  static TypeRegistry typeRegisry(String... types) {
    TypeRegistry registry = new TypeRegistry();
    for (String type : types) {
      registry.registerType(type);
    }
    return registry;
  }
}

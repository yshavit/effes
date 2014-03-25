package com.yuvalshavit.effes.compile;

import com.google.common.collect.Sets;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;
import org.testng.annotations.Test;

import java.io.IOException;

import static com.yuvalshavit.util.AssertException.assertException;
import static org.testng.Assert.assertEquals;

public final class TypesFinderTest {
  @Test
  public void findDataTypes() throws IOException {
    EffesParser parser = ParserUtils.createParser(
      "data type True",
      "data type False");
    TypeRegistry registry = new TypeRegistry();
    new TypesFinder(registry).accept(parser);
    assertEquals(registry.getAllSimpleTypeNames(), Sets.newHashSet("True", "False"));
  }

  @Test
  public void duplicateTypes() {
    EffesParser parser = ParserUtils.createParser(
      "data type True",
      "data type True"
    );
    TypeRegistry registry = new TypeRegistry();
    assertException(TypeRegistryException.class, () -> new TypesFinder(registry).accept(parser));
  }
}
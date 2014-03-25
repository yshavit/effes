package com.yuvalshavit.effes.compile;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.testng.Assert.assertEquals;

public final class TypesFinderTest {
  @Test
  public void findDataTypes() throws IOException {
    String code = Joiner.on('\n').join(
      "data type True",
      "data type False"
    );
    EffesParser parser = ParserUtils.createParser(code);
    TypeRegistry registry = new TypeRegistry();
    new TypesFinder(registry).accept(parser);
    assertEquals(registry.getAllSimpleTypeNames(), Sets.newHashSet("True", "False"));
  }
}

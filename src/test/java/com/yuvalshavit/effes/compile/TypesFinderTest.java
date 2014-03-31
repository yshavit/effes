package com.yuvalshavit.effes.compile;

import com.google.common.collect.Sets;
import com.yuvalshavit.effes.TUtils;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.testng.Assert.assertEquals;

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
}

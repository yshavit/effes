package com.yuvalshavit.effes.compile;

import java.util.Arrays;
import java.util.Collections;

import com.yuvalshavit.effes.parser.EffesParser;

public class SourcesFactory {
  private SourcesFactory() {}
  
  public static Sources withoutBuiltins(EffesParser parser) {
    return withoutBuiltins(parser.compilationUnit());
  }

  private static Sources withoutBuiltins(EffesParser.CompilationUnitContext... compilationUnitContexts) {
    return new Sources(Collections.emptySet(), Arrays.asList(compilationUnitContexts));
  }
}

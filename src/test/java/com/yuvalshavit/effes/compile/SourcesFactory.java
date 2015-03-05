package com.yuvalshavit.effes.compile;

import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.yuvalshavit.effes.parser.EffesParser;

public class SourcesFactory {
  private SourcesFactory() {}
  
  public static Sources withoutBuiltins(EffesParser parser) {
    return withoutBuiltins(parser.compilationUnit());
  }

  private static Sources withoutBuiltins(EffesParser.CompilationUnitContext... compilationUnitContexts) {
    return new Sources(Collections.emptySet(), Stream.of(compilationUnitContexts).map(Source::new).collect(Collectors.toList()));
  }
}

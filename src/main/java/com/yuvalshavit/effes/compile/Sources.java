package com.yuvalshavit.effes.compile;


import java.util.Arrays;
import java.util.Collections;
import java.util.function.Consumer;

import com.google.common.collect.Iterables;
import com.yuvalshavit.effes.parser.EffesParser;

public class Sources {
  
  private final Iterable<EffesParser.CompilationUnitContext> builtins;
  private final Iterable<EffesParser.CompilationUnitContext> userland;

  public Sources(Iterable<EffesParser.CompilationUnitContext> builtins, Iterable<EffesParser.CompilationUnitContext> userland) {
    this.builtins = builtins;
    this.userland = userland;
  }
  
  public Sources(Iterable<EffesParser.CompilationUnitContext> userland) {
    this(defaultBuiltinSources(), userland);
  }

  public Sources(EffesParser.CompilationUnitContext... compilationUnitContexts) {
    this(Arrays.asList(compilationUnitContexts));
  }

  public void forEach(Consumer<? super EffesParser.CompilationUnitContext> action) {
    allSources().forEach(action);
  }

  private Iterable<EffesParser.CompilationUnitContext> allSources() {
    return Iterables.concat(builtins, userland);
  }

  private static Iterable<EffesParser.CompilationUnitContext> defaultBuiltinSources() {
    // TODO
    return Collections.emptySet();
  }
  
}

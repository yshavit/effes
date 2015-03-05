package com.yuvalshavit.effes.compile;


import java.util.Arrays;
import java.util.Collections;
import java.util.function.Consumer;

import com.google.common.collect.Iterables;
import com.yuvalshavit.effes.parser.EffesParser;

public class Sources {
  
  private final Iterable<Source> builtins;
  private final Iterable<Source> userland;

  public Sources(Iterable<EffesParser.CompilationUnitContext> builtins, Iterable<EffesParser.CompilationUnitContext> userland) {
    this.builtins = Iterables.transform(builtins, c -> new Source(c, true));
    this.userland = Iterables.transform(userland, c -> new Source(c, false));
  }
  
  public Sources(Iterable<EffesParser.CompilationUnitContext> userland) {
    this(defaultBuiltinSources(), userland);
  }

  public Sources(EffesParser.CompilationUnitContext... compilationUnitContexts) {
    this(Arrays.asList(compilationUnitContexts));
  }

  public void forEach(Consumer<? super Source> action) {
    allSources().forEach(action);
  }

  private Iterable<Source> allSources() {
    return Iterables.concat(builtins, userland);
  }

  private static Iterable<EffesParser.CompilationUnitContext> defaultBuiltinSources() {
    // TODO
    return Collections.emptySet();
  }

}

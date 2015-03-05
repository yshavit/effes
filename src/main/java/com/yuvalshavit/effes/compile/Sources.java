package com.yuvalshavit.effes.compile;


import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Consumer;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;
import com.yuvalshavit.util.RelativeUrl;

public class Sources {
  
  private final Iterable<Source> builtins;
  private final Iterable<Source> userland;

  public Sources(Iterable<Source> builtins, Iterable<Source> userland) {
    this.builtins = ImmutableList.copyOf(builtins);
    this.userland = ImmutableList.copyOf(userland);
  }
  
  public Sources(Iterable<Source> userland) {
    this(defaultBuiltinSources(), userland);
  }

  public Sources(Source... compilationUnitContexts) {
    this(Arrays.asList(compilationUnitContexts));
  }

  public void forEach(Consumer<? super Source> action) {
    allSources().forEach(action);
  }

  private Iterable<Source> allSources() {
    return Iterables.concat(builtins, userland);
  }

  private static Iterable<Source> defaultBuiltinSources() {
    RelativeUrl relativeUrl = new RelativeUrl(Sources.class);
    URL url = relativeUrl.get("Builtin.ef");
    Collection<Source> contexts = new ArrayList<>();
    try (InputStream is = url.openStream(); InputStreamReader reader = new InputStreamReader(is, Charsets.UTF_8)) {
      EffesParser parser = ParserUtils.createParser(reader);
      Source source = new Source(parser.compilationUnit(), true);
      contexts.add(source);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
    return contexts;
  }

}

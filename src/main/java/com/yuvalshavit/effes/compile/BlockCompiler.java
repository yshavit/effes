package com.yuvalshavit.effes.compile;

import com.google.common.collect.ImmutableList;
import com.yuvalshavit.effes.parser.EffesParser;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class BlockCompiler implements Function<EffesParser.InlinableBlockContext, Block> {
  private final StatementCompiler compiler;

  public BlockCompiler(StatementCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public Block apply(EffesParser.InlinableBlockContext inlinableBlockContext) {
    if (inlinableBlockContext == null) {
      return new Block(ImmutableList.of());
    }
    List<Statement> stats = inlinableBlockContext.stat().stream().map(compiler).collect(Collectors.toList());
    return new Block(stats);
  }
}

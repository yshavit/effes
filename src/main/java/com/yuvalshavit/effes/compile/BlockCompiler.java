package com.yuvalshavit.effes.compile;

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
    List<Statement> stats = inlinableBlockContext.stat().stream().map(compiler).collect(Collectors.toList());
    return new Block(stats);
  }
}

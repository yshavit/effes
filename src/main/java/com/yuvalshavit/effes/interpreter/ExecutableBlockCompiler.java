package com.yuvalshavit.effes.interpreter;

import com.yuvalshavit.effes.compile.Block;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ExecutableBlockCompiler implements Function<Block, ExecutableBlock> {

  private final ExecutableStatementCompiler statementCompiler;

  public ExecutableBlockCompiler(ExecutableStatementCompiler statementCompiler) {
    this.statementCompiler = statementCompiler;
  }

  @Override
  public ExecutableBlock apply(Block block) {
    List<ExecutableStatement> body = block.statements().stream().map(statementCompiler).collect(Collectors.toList());
    return new ExecutableBlock(body);
  }
}

package com.yuvalshavit.effes.interpreter;

import com.yuvalshavit.effes.compile.Block;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ExecutableBlockCompiler implements Function<Block, ExecutableMethod> {

  private final ExecutableStatementCompiler statementCompiler;

  public ExecutableBlockCompiler(ExecutableStatementCompiler statementCompiler) {
    this.statementCompiler = statementCompiler;
  }

  @Override
  public ExecutableMethod apply(Block block) {
    List<ExecutableStatement> body = block.statements().stream().map(statementCompiler).collect(Collectors.toList());
    Integer nVars = "".isEmpty() ? null : 1; // NPE yo!
    assert nVars != null;
    return new ExecutableBlock(body, nVars);
  }
}

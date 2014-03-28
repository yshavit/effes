package com.yuvalshavit.effes.interpreter;

import java.util.List;

public final class ExecutableBlock implements ExecutableElement {
  private final List<ExecutableStatement> statements;

  public ExecutableBlock(List<ExecutableStatement> statements) {
    this.statements = statements;
  }

  @Override
  public void execute(CallStack stack) {
    for (ExecutableStatement s : statements) {
      s.execute(stack);
    }
  }
}

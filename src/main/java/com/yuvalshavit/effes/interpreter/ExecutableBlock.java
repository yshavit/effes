package com.yuvalshavit.effes.interpreter;

import java.util.List;

public final class ExecutableBlock implements ExecutableMethod {
  private final List<ExecutableStatement> statements;
  private final int nVars;

  public ExecutableBlock(List<ExecutableStatement> statements, int nVars) {
    this.statements = statements;
    this.nVars = nVars;
  }

  @Override
  public void execute(CallStack stack) {
    for (ExecutableStatement s : statements) {
      s.execute(stack);
      if (stack.rvIsSet()) {
        return;
      }
    }
  }

  @Override
  public int nVars() {
    return nVars;
  }
}

package com.yuvalshavit.effes.interpreter;

import com.yuvalshavit.effes.compile.Statement;
import com.yuvalshavit.util.Dispatcher;

import java.util.function.Function;

public final class ExecutableStatementCompiler implements Function<Statement, ExecutableStatement> {

  private final ExecutableExpressionCompiler expressionCompiler;

  public ExecutableStatementCompiler(ExecutableExpressionCompiler expressionCompiler) {
    this.expressionCompiler = expressionCompiler;
  }

  @Override
  public ExecutableStatement apply(Statement statement) {
    return StatementCompiler.dispatcher.apply(new StatementCompiler(expressionCompiler), statement);
  }

  static class StatementCompiler {
    static final Dispatcher<StatementCompiler, Statement, ExecutableStatement> dispatcher =
      Dispatcher.builder(StatementCompiler.class, Statement.class, ExecutableStatement.class)
        .put(Statement.ReturnStatement.class, StatementCompiler::returnStat)
        .build();

    private final ExecutableExpressionCompiler expressionCompiler;

    StatementCompiler(ExecutableExpressionCompiler expressionCompiler) {
      this.expressionCompiler = expressionCompiler;
    }

    public ExecutableStatement returnStat(Statement.ReturnStatement returnStatement) {
      return new ExecutableStatement.ReturnStatement(returnStatement, expressionCompiler);
    }
  }
}

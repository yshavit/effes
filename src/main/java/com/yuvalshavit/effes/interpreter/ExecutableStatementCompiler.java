package com.yuvalshavit.effes.interpreter;

import com.yuvalshavit.effes.compile.Statement;
import com.yuvalshavit.util.Dispatcher;

import java.util.function.Function;

public final class ExecutableStatementCompiler implements Function<Statement, ExecutableStatement> {

  private final ExecutableExpressionCompiler expressionCompiler;

  public ExecutableStatementCompiler(ExecutableExpressionCompiler expressionCompiler)
  {
    this.expressionCompiler = expressionCompiler;
  }

  @Override
  public ExecutableStatement apply(Statement statement) {
    return dispatcher.apply(this, statement);
  }

  private static final Dispatcher<ExecutableStatementCompiler, Statement, ExecutableStatement> dispatcher =
    Dispatcher.builder(ExecutableStatementCompiler.class, Statement.class, ExecutableStatement.class)
      .put(Statement.AssignStatement.class, ExecutableStatementCompiler::assignStatement)
      .put(Statement.MethodInvoke.class, ExecutableStatementCompiler::methodInvoke)
      .put(Statement.ReturnStatement.class, ExecutableStatementCompiler::returnStat)
      .put(Statement.UnrecognizedStatement.class, (me, s) -> { throw new AssertionError(s); })
      .build((c, s) -> {throw new AssertionError(s);});

  private ExecutableStatement assignStatement(Statement.AssignStatement stat) {
    return new ExecutableStatement.AssignStatement(stat, expressionCompiler);
  }

  private ExecutableStatement methodInvoke(Statement.MethodInvoke stat) {
    return new ExecutableStatement.MethodInvoke(stat, expressionCompiler, stat.methodExpr());
  }

  private ExecutableStatement returnStat(Statement.ReturnStatement returnStatement) {
    return new ExecutableStatement.ReturnStatement(returnStatement, expressionCompiler);
  }
}

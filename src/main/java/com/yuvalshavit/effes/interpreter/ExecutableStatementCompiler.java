package com.yuvalshavit.effes.interpreter;

import com.google.common.base.Suppliers;
import com.yuvalshavit.effes.compile.Statement;
import com.yuvalshavit.util.Dispatcher;

import java.util.function.Function;
import java.util.function.Supplier;

public final class ExecutableStatementCompiler implements Function<Statement, ExecutableStatement> {

  private final StatementCompiler statementCompiler;

  public ExecutableStatementCompiler(ExecutableExpressionCompiler expressionCompiler,
                                     Function<String, ExecutableBlock> methods)
  {
    this.statementCompiler = new StatementCompiler(methods, expressionCompiler);
  }

  @Override
  public ExecutableStatement apply(Statement statement) {
    return StatementCompiler.dispatcher.apply(statementCompiler, statement);
  }

  static class StatementCompiler {
    static final Dispatcher<StatementCompiler, Statement, ExecutableStatement> dispatcher =
      Dispatcher.builder(StatementCompiler.class, Statement.class, ExecutableStatement.class)
        .put(Statement.MethodInvoke.class, StatementCompiler::methodInvoke)
        .put(Statement.ReturnStatement.class, StatementCompiler::returnStat)
        .build();

    private final Function<String, ExecutableBlock> methods;
    private final ExecutableExpressionCompiler expressionCompiler;

    StatementCompiler(Function<String, ExecutableBlock> methods, ExecutableExpressionCompiler expressionCompiler) {
      this.methods = methods;
      this.expressionCompiler = expressionCompiler;
    }

    public ExecutableStatement methodInvoke(Statement.MethodInvoke methodInvoke) {
      com.google.common.base.Supplier<ExecutableBlock> memoized = Suppliers.memoize(() -> {
        String methodName = methodInvoke.getMethodName();
        ExecutableBlock method = methods.apply(methodName);
        assert method != null : methodName;
        return method;
      });
      Supplier<ExecutableBlock> getter = memoized::get; // convert to java.lang.function.Supplier
      return new ExecutableStatement.MethodInvoke(methodInvoke, expressionCompiler, getter);
    }

    public ExecutableStatement returnStat(Statement.ReturnStatement returnStatement) {
      return new ExecutableStatement.ReturnStatement(returnStatement, expressionCompiler);
    }
  }
}

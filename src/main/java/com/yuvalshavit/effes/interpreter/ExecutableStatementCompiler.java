package com.yuvalshavit.effes.interpreter;

import com.google.common.base.Suppliers;
import com.yuvalshavit.effes.compile.Statement;
import com.yuvalshavit.util.Dispatcher;

import java.util.function.Function;
import java.util.function.Supplier;

public final class ExecutableStatementCompiler implements Function<Statement, ExecutableStatement> {

  private final Function<String, ExecutableBlock> methods;
  private final ExecutableExpressionCompiler expressionCompiler;

  public ExecutableStatementCompiler(ExecutableExpressionCompiler expressionCompiler,
                                     Function<String, ExecutableBlock> methods)
  {
    this.expressionCompiler = expressionCompiler;
    this.methods = methods;
  }

  @Override
  public ExecutableStatement apply(Statement statement) {
    return dispatcher.apply(this, statement);
  }

  private static final Dispatcher<ExecutableStatementCompiler, Statement, ExecutableStatement> dispatcher =
    Dispatcher.builder(ExecutableStatementCompiler.class, Statement.class, ExecutableStatement.class)
      .put(Statement.MethodInvoke.class, ExecutableStatementCompiler::methodInvoke)
      .put(Statement.ReturnStatement.class, ExecutableStatementCompiler::returnStat)
      .build();

  private ExecutableStatement methodInvoke(Statement.MethodInvoke methodInvoke) {
    com.google.common.base.Supplier<ExecutableBlock> memoized = Suppliers.memoize(() -> {
      String methodName = methodInvoke.getMethodName();
      ExecutableBlock method = methods.apply(methodName);
      assert method != null : methodName;
      return method;
    });
    Supplier<ExecutableBlock> getter = memoized::get; // convert to java.lang.function.Supplier
    return new ExecutableStatement.MethodInvoke(methodInvoke, expressionCompiler, getter);
  }

  private ExecutableStatement returnStat(Statement.ReturnStatement returnStatement) {
    return new ExecutableStatement.ReturnStatement(returnStatement, expressionCompiler);
  }
}

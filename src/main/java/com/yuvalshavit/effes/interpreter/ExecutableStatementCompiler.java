package com.yuvalshavit.effes.interpreter;

import com.google.common.collect.Lists;
import com.yuvalshavit.effes.compile.Block;
import com.yuvalshavit.effes.compile.EfMethod;
import com.yuvalshavit.effes.compile.MethodsRegistry;
import com.yuvalshavit.effes.compile.Statement;
import com.yuvalshavit.util.Dispatcher;

import java.util.List;
import java.util.function.Function;

public final class ExecutableStatementCompiler implements Function<Statement, ExecutableStatement> {

  private final StatementCompiler statementCompiler;

  public ExecutableStatementCompiler(ExecutableExpressionCompiler expressionCompiler, MethodsRegistry<Block> methods) {
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

    private final MethodsRegistry<Block> methods;
    private final ExecutableExpressionCompiler expressionCompiler;

    StatementCompiler(MethodsRegistry<Block> methods, ExecutableExpressionCompiler expressionCompiler) {
      this.methods = methods;
      this.expressionCompiler = expressionCompiler;
    }

    public ExecutableStatement methodInvoke(Statement.MethodInvoke methodInvoke) {
      EfMethod<Block> method = methods.getMethod(methodInvoke.getMethodName());
      assert method != null : methodInvoke.getMethodName();
      List<ExecutableStatement> body = Lists.transform(method.getBody().statements(), s -> dispatcher.apply(this, s));
      return new ExecutableStatement.MethodInvoke(methodInvoke, expressionCompiler, body);
    }

    public ExecutableStatement returnStat(Statement.ReturnStatement returnStatement) {
      return new ExecutableStatement.ReturnStatement(returnStatement, expressionCompiler);
    }
  }
}

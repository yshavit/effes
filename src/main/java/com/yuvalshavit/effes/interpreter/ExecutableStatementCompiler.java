package com.yuvalshavit.effes.interpreter;

import com.google.common.base.Suppliers;
import com.yuvalshavit.effes.compile.Statement;
import com.yuvalshavit.util.Dispatcher;

import java.util.function.Function;
import java.util.function.Supplier;

public final class ExecutableStatementCompiler implements Function<Statement, ExecutableStatement> {

  private final Function<String, ExecutableElement> methods;
  private final ExecutableExpressionCompiler expressionCompiler;
  private final Function<String, ExecutableElement> builtInMethods;

  public ExecutableStatementCompiler(ExecutableExpressionCompiler expressionCompiler,
                                     Function<String, ExecutableElement> methods,
                                     Function<String, ExecutableElement> builtInMethods)
  {
    this.expressionCompiler = expressionCompiler;
    this.methods = methods;
    this.builtInMethods = builtInMethods;
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

  private ExecutableStatement methodInvoke(Statement.MethodInvoke stat) {
    Function<String, ExecutableElement> lookup = stat.isBuiltIn()
      ? builtInMethods
      : methods;
    return methodInvoke(stat, lookup);
  }

  private ExecutableStatement methodInvoke(Statement.MethodInvoke stat, Function<String, ExecutableElement> lookup) {
    com.google.common.base.Supplier<ExecutableElement> memoized = Suppliers.memoize(() -> {
      String methodName = stat.getMethodName();
      ExecutableElement method = lookup.apply(methodName);
      assert method != null : methodName;
      return method;
    });
    Supplier<ExecutableElement> getter = memoized::get; // convert to java.lang.function.Supplier
    return new ExecutableStatement.MethodInvoke(stat, expressionCompiler, getter);
  }

  private ExecutableStatement returnStat(Statement.ReturnStatement returnStatement) {
    return new ExecutableStatement.ReturnStatement(returnStatement, expressionCompiler);
  }
}

package com.yuvalshavit.effes.interpreter;

import com.google.common.collect.Lists;
import com.yuvalshavit.effes.compile.Expression;
import com.yuvalshavit.effes.compile.Statement;
import com.yuvalshavit.effes.TUtils;
import com.yuvalshavit.effes.compile.TypeRegistry;
import com.yuvalshavit.util.Dispatcher;
import com.yuvalshavit.util.DispatcherTestBase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public final class ExecutableStatementCompilerTest extends DispatcherTestBase {

  @DataProvider(name = DispatcherTestBase.providerName)
  public static Object[][] statementSubclasses() {
    return DispatcherTestBase.findSubclasses(Statement.class);
  }

  @Override
  protected Dispatcher<?, ?, ?> getDispatcherUnderTest() {
    return ExecutableStatementCompiler.StatementCompiler.dispatcher;
  }

  @Test
  public void returnStat() {
    TypeRegistry typeRegistry = TUtils.typeRegistry("True");
    Expression trueCtor = new Expression.CtorInvoke(TUtils.getExistingType(typeRegistry, "True"));
    Statement returnStat = new Statement.ReturnStatement(trueCtor);
    ExecutableExpressionCompiler expressionCompiler = new ExecutableExpressionCompiler();
    ExecutableStatementCompiler statementCompiler = new ExecutableStatementCompiler(expressionCompiler);
    ExecutableStatement executable = statementCompiler.apply(returnStat);
    StateStack states = new StateStack();
    executable.execute(states);
    assertEquals(states.getAllStates(), Lists.newArrayList(typeRegistry.getSimpleType("True")));
  }
}

package com.yuvalshavit.effes.interpreter;

import com.yuvalshavit.effes.compile.Expression;
import com.yuvalshavit.util.Dispatcher;
import com.yuvalshavit.util.DispatcherTestBase;
import org.testng.annotations.DataProvider;

public final class ExecutableExpressionCompilerTest extends DispatcherTestBase {

  @DataProvider(name = DispatcherTestBase.providerName)
  public Object[][] expressionSubclasses() {
    return DispatcherTestBase.findSubclasses(Expression.class);
  }

  @Override
  protected Dispatcher<?, ?, ?> getDispatcherUnderTest() {
    return ExecutableExpressionCompiler.ExpressionDispatcher.dispatcher;
  }
}

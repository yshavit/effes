package com.yuvalshavit.effes.compile.expr;

import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.util.Dispatcher;
import com.yuvalshavit.util.DispatcherTestBase;
import org.testng.annotations.DataProvider;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExpressionCompilerTest extends DispatcherTestBase {
  @DataProvider(name = DispatcherTestBase.providerName)
  public static Object[][] getCases() {
    return Stream.of(EffesParser.class.getClasses())
      .filter(EffesParser.ExprContext.class::isAssignableFrom)
      .map(c -> new Object[]{ c })
      .collect(Collectors.toList())
      .toArray(new Object[0][]);
  }

  @Override
  protected Dispatcher<?, ?, ?> getDispatcherUnderTest() {
    return ExpressionCompiler.Dispatch.dispatcher;
  }
}

package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;
import com.yuvalshavit.util.Dispatcher;
import com.yuvalshavit.util.DispatcherTestBase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static com.yuvalshavit.effes.compile.TUtils.getExistingType;
import static org.testng.Assert.assertEquals;

public final class StatementCompilerTest extends DispatcherTestBase {

  @DataProvider(name=DispatcherTestBase.providerName)
  public static Object[][] statSubclasses() {
    return findSubclasses(EffesParser.class, EffesParser.StatContext.class);
  }

  @Override
  protected Dispatcher<?, ?, ?> getDispatcherUnderTest() {
    return StatementCompiler.StatementDispatcher.dispatcher;
  }

  @Test
  public void returnStat() {
    TypeRegistry registry = TUtils.typeRegistry("True");
    EffesParser.StatContext statContext = parseStatement("return True");
    ExpressionCompiler expressionCompiler = new ExpressionCompiler(registry);
    Statement compiled = new StatementCompiler(expressionCompiler).apply(statContext);
    Expression trueExpr = new Expression.CtorInvoke(getExistingType(registry, "True"));
    assertEquals(compiled, new Statement.ReturnStatement(trueExpr));
  }

  private static EffesParser.StatContext parseStatement(String code) {
    return ParserUtils.parseRule(EffesParser.StatContext.class, code);
  }
}

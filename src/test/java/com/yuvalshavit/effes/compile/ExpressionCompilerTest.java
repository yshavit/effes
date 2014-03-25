package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;
import com.yuvalshavit.util.Dispatcher;
import com.yuvalshavit.util.DispatcherTestBase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static com.yuvalshavit.util.AssertException.assertException;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public final class ExpressionCompilerTest extends DispatcherTestBase {

  @DataProvider(name=DispatcherTestBase.providerName)
  public static Object[][] exprSubclasses() {
    Class<EffesParser> lookIn = EffesParser.class;
    Class<EffesParser.ExprContext> baseClass = EffesParser.ExprContext.class;
    return findSubclasses(lookIn, baseClass);
  }

  @Test
  public void ctorInvoke() {
    TypeRegistry registry = MethodsFinderTest.typeRegisry("True");
    EffesParser.ExprContext exprContext = parseException("True");
    Expression compiled = new ExpressionCompiler(registry).apply(exprContext);
    assertEquals(compiled, new Expression.CtorInvoke(getExistingType(registry, "True")));
  }

  @Test
  public void ctorInvokeUnknownType() {
    TypeRegistry registry = MethodsFinderTest.typeRegisry("True");
    EffesParser.ExprContext exprContext = parseException("False");
    assertException(ExpressionCompilationException.class, () -> new ExpressionCompiler(registry).apply(exprContext));
  }

  @Test
  public void param() {
    TypeRegistry registry = MethodsFinderTest.typeRegisry("True");
    EffesParser.ExprContext exprContext = parseException("(True)");
    Expression compiled = new ExpressionCompiler(registry).apply(exprContext);
    // Note that the compiled expression is inlined -- there's no Expression.Param
    assertEquals(compiled, new Expression.CtorInvoke(getExistingType(registry, "True")));
  }

  private EffesParser.ExprContext parseException(String code) {
    EffesParser parser = ParserUtils.createParser(code);
    return ParserUtils.ruleByName(parser, EffesParser.ExprContext.class);
  }

  private SimpleType getExistingType(TypeRegistry registry, String name) {
    SimpleType type = registry.getSimpleType(name);
    assertNotNull(type, name);
    return type;
  }

  @Override
  protected Dispatcher<?, ?, ?> getDispatcherUnderTest() {
    return ExpressionCompiler.ExpressionDispatcher.dispatcher;
  }
}

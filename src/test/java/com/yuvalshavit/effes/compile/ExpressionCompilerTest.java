package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.TUtils;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public final class ExpressionCompilerTest {

  @Test
  public void ctorInvoke() {
    TypeRegistry registry = TUtils.typeRegistry("True");
    EffesParser.ExprContext exprContext = parseExpression("True");
    Expression compiled = new ExpressionCompiler(null, null, registry, CompileErrors.throwing, null).apply(exprContext);

    assertEqualStrings(compiled, new Expression.CtorInvoke(null, TUtils.getExistingType(registry, "True")));
  }

  @Test
  public void ctorInvokeUnknownType() {
    TypeRegistry registry = TUtils.typeRegistry("True");
    EffesParser.ExprContext exprContext = parseExpression("False");
    TUtils.expectErrors(errs -> new ExpressionCompiler(null, null, registry, errs, null).apply(exprContext));
  }

  @Test
  public void param() {
    TypeRegistry registry = TUtils.typeRegistry("True");
    EffesParser.ExprContext exprContext = parseExpression("(True)");
    Expression compiled = new ExpressionCompiler(null, null, registry, CompileErrors.throwing, null).apply(exprContext);
    // Note that the compiled expression is inlined -- there's no Expression.Param
    assertEqualStrings(compiled, new Expression.CtorInvoke(null, TUtils.getExistingType(registry, "True")));
  }

  private static EffesParser.ExprContext parseExpression(String code) {
    return ParserUtils.parseRule(EffesParser.ExprContext.class, code);
  }

  private static void assertEqualStrings(Object one, Object two) {
    assertEquals(one.toString(), two.toString());
  }
}

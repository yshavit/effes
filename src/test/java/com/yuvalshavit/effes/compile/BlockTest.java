package com.yuvalshavit.effes.compile;

import com.google.common.collect.ImmutableList;
import com.yuvalshavit.effes.TUtils;
import org.testng.annotations.Test;

public final class BlockTest {
  private final TypeRegistry typeRegistry = TUtils.typeRegistry("True", "False", "Unknown");

  @Test
  public void singleReturn() {
    Block block = new Block(type("True"), ImmutableList.of(returnStat(ctorExpr("True"))));
    CompileErrors errs = new CompileErrors();
    block.validate(errs);
    block.validate(errs);
  }

  @Test
  public void singleReturnWrongType() {
    Block block = new Block(type("False"), ImmutableList.of(returnStat(ctorExpr("True"))));
    CompileErrors errs = new CompileErrors();
    TUtils.expectErrors(errs, () -> block.validate(errs));
  }

  @Test
  public void noReturns() {
    Block block = new Block(EfType.VOID, ImmutableList.of());
    TUtils.expectNoErrors(block::validate);
  }

  @Test
  public void noReturnsButNeedOne() {
    Block block = new Block(type("True"), ImmutableList.of());
    TUtils.expectErrors(block::validate);
  }

  @Test
  public void twoReturns() {
    Block block = new Block(type("True"), ImmutableList.of(
      returnStat(ctorExpr("True")),
      returnStat(ctorExpr("True"))));
    TUtils.expectErrors(block::validate);
  }

  private Statement.ReturnStatement returnStat(Expression expr) {
    return new Statement.ReturnStatement(null, expr);
  }

  private Expression.CtorInvoke ctorExpr(String typeName) {
    return new Expression.CtorInvoke(null, type(typeName), ImmutableList.of());
  }

  private EfType.SimpleType type(String typeName) {
    return TUtils.getExistingType(typeRegistry, typeName);
  }
}

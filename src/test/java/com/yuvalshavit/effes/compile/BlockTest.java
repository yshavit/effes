package com.yuvalshavit.effes.compile;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.Test;

import static com.yuvalshavit.util.AssertException.assertException;

public final class BlockTest {
  private final TypeRegistry typeRegistry = TUtils.typeRegistry("True", "False");

  @Test
  public void singleReturn() {
    Block block = new Block(ImmutableList.of(returnStat(ctorExpr("True"))));
    block.validate(null);
    block.validate(type("True"));
    assertException(BlockValidationException.class, () -> block.validate(type("False")));
  }

  @Test
  public void noReturns() {
    Block block = new Block(ImmutableList.of());
    block.validate(null);
    assertException(BlockValidationException.class, () -> block.validate(type("True")));
    assertException(BlockValidationException.class, () -> block.validate(type("False")));
  }

  @Test
  public void twoReturns() {
    Block block = new Block(ImmutableList.of(
      returnStat(ctorExpr("True")),
      returnStat(ctorExpr("True"))));
    assertException(BlockValidationException.class, () -> block.validate(null));
    assertException(BlockValidationException.class, () -> block.validate(type("True")));
    assertException(BlockValidationException.class, () -> block.validate(type("False")));
  }

  private Statement.ReturnStatement returnStat(Expression expr) {
    return new Statement.ReturnStatement(expr);
  }

  private Expression.CtorInvoke ctorExpr(String typeName) {
    return new Expression.CtorInvoke(type(typeName));
  }

  private SimpleType type(String typeName) {
    return TUtils.getExistingType(typeRegistry, typeName);
  }
}

package com.yuvalshavit.effes.compile;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.Test;

import static com.yuvalshavit.util.AssertException.assertException;

public final class BlockTest {
  private final TypeRegistry typeRegistry = TUtils.typeRegistry("True");

  @Test
  public void singleReturn() {
    Block block = new Block(ImmutableList.of(returnStat(ctorExpr("True"))));
    block.validate(false);
    block.validate(true);
  }

  @Test
  public void noReturns() {
    Block block = new Block(ImmutableList.of());
    block.validate(false);
    assertException(BlockValidationException.class, () -> block.validate(true));
  }

  @Test
  public void twoReturns() {
    Block block = new Block(ImmutableList.of(
      returnStat(ctorExpr("True")),
      returnStat(ctorExpr("True"))));
    assertException(BlockValidationException.class, () -> block.validate(false));
    assertException(BlockValidationException.class, () -> block.validate(true));
  }

  private Statement.ReturnStatement returnStat(Expression expr) {
    return new Statement.ReturnStatement(expr);
  }

  private Expression.CtorInvoke ctorExpr(String typeName) {
    return new Expression.CtorInvoke(TUtils.getExistingType(typeRegistry, typeName));
  }
}

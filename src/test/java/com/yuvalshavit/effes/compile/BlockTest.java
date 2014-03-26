package com.yuvalshavit.effes.compile;

import com.google.common.collect.ImmutableList;
import com.yuvalshavit.effes.TUtils;
import org.testng.annotations.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yuvalshavit.util.AssertException.assertException;

public final class BlockTest {
  private final TypeRegistry typeRegistry = TUtils.typeRegistry("True", "False", "Unknown");

  @Test
  public void singleReturn() {
    Block block = new Block(ImmutableList.of(returnStat(ctorExpr("True"))));
    block.validate(null);
    block.validate(type("True"));
    assertException(BlockValidationException.class, () -> block.validate(type("False")));
    block.validate(disjunct("True", "False"));
  }

  @Test
  public void todo() {
    throw new UnsupportedOperationException("need more tests"); // TODO
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
    return new Statement.ReturnStatement(null, expr);
  }

  private Expression.CtorInvoke ctorExpr(String typeName) {
    return new Expression.CtorInvoke(null, type(typeName));
  }

  private EfType.SimpleType type(String typeName) {
    return TUtils.getExistingType(typeRegistry, typeName);
  }

  private EfType disjunct(String... typeName) {
    List<EfType> simples = Stream.of(typeName).map(this::type).collect(Collectors.toList());
    return new EfType.DisjunctiveType(simples);
  }
}

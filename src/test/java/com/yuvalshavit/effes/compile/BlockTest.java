package com.yuvalshavit.effes.compile;

import com.google.common.collect.ImmutableList;
import com.yuvalshavit.effes.TUtils;
import org.testng.annotations.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class BlockTest {
  private final TypeRegistry typeRegistry = TUtils.typeRegistry("True", "False", "Unknown");

  @Test
  public void singleReturn() {
    Block block = new Block(ImmutableList.of(returnStat(ctorExpr("True"))));
    CompileErrors errs = new CompileErrors();
    block.validate(null, errs);
    block.validate(type("True"), errs);
    TUtils.expectErrors(errs, () -> block.validate(type("False"), errs));
  }

  @Test
  public void noReturns() {
    Block block = new Block(ImmutableList.of());
    TUtils.expectNoErrors(errs -> block.validate(null, errs));
    TUtils.expectErrors(errs -> block.validate(type("True"), errs));
    TUtils.expectErrors(errs -> block.validate(type("False"), errs));
  }

  @Test
  public void twoReturns() {
    Block block = new Block(ImmutableList.of(
      returnStat(ctorExpr("True")),
      returnStat(ctorExpr("True"))));
    TUtils.expectErrors(errs -> block.validate(null, errs));
    TUtils.expectErrors(errs -> block.validate(type("True"), errs));
    TUtils.expectErrors(errs -> block.validate(type("False"), errs));
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

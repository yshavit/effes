package com.yuvalshavit.effes.compile.expr;

import com.google.common.collect.Sets;
import com.yuvalshavit.effes.parser.EffesParser;
import org.junit.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.testng.AssertJUnit.assertEquals;

public class DispatchTest {

  @Test
  public void methods() {
    Set<Class<?>> exprClasses = findExprClasses();
    Set<Class<?>> dispatchedClasses = Arrays.asList(ExpressionCompiler.Dispatch.class.getDeclaredMethods())
      .stream()
      .filter(m -> m.getParameterTypes().length == 1)
      .map(m -> m.getParameterTypes()[0])
      .collect(Collectors.toSet());
    Set<Class<?>> diff = Sets.difference(exprClasses, dispatchedClasses);
    assertEquals("diff", Sets.<Class<?>>newHashSet(), diff);
  }

  @Test
  public void dispatchMap() {
    Set<Class<?>> exprClasses = findExprClasses();
    Set<Class<?>> dispatchKeys = ExpressionCompiler.Dispatch.dispatchMap.keySet();
    Set<Class<?>> diff = Sets.difference(exprClasses, dispatchKeys);
    assertEquals("diff", Sets.<Class<?>>newHashSet(EffesParser.ExprContext.class), diff);
  }

  private Set<Class<?>> findExprClasses() {
    return Arrays.asList(EffesParser.class.getDeclaredClasses())
      .stream()
      .filter(EffesParser.ExprContext.class::isAssignableFrom)
      .collect(Collectors.toSet());
  }
}

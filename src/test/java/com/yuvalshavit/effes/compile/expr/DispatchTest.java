package com.yuvalshavit.effes.compile.expr;

import com.google.common.collect.Sets;
import com.yuvalshavit.effes.parser.EffesParser;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.testng.AssertJUnit.assertEquals;

public class DispatchTest {

  @DataProvider(name = "classes")
  public static Object[][] classes() {
    return new Object[][] {
      new Object[] { ExpressionCompiler.Dispatch.class, EffesParser.ExprContext.class },
      new Object[] {StatementCompiler.Dispatch.class, EffesParser.StatContext.class }
    };
  }

  private final Class<?> dispatcherClass;
  private final Class<?> contextBase;

  @Factory(dataProvider = "classes")
  public DispatchTest(Class<?> dispatcherClass, Class<?> contextBase) {
    this.dispatcherClass = dispatcherClass;
    this.contextBase = contextBase;
  }

  @Test
  public void methods() {
    Set<Class<?>> exprClasses = findExprClasses();
    Set<Class<?>> dispatchedClasses = Arrays.asList(dispatcherClass.getDeclaredMethods())
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
    Set<Class<?>> dispatchKeys = getDispatchKeys();// ExpressionCompiler.Dispatch.dispatchMap.keySet();
    Set<Class<?>> diff = Sets.difference(exprClasses, dispatchKeys);
    assertEquals("diff", Sets.<Class<?>>newHashSet(contextBase), diff);
  }

  private Set<Class<?>> getDispatchKeys() {
    try {
      Field dispatchMap = dispatcherClass.getField("dispatchMap");
      Map<?, ?> m = (Map<?, ?>) dispatchMap.get(null);
      Set<Class<?>> keys = Sets.newHashSet();
      for (Object o : m.keySet()) {
        Class<?> c = (Class<?>) o;
        keys.add(c);
      }
      return keys;
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private Set<Class<?>> findExprClasses() {
    return Arrays.asList(EffesParser.class.getDeclaredClasses())
      .stream()
      .filter(contextBase::isAssignableFrom)
      .collect(Collectors.toSet());
  }
}

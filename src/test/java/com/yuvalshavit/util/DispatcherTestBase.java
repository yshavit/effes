package com.yuvalshavit.util;

import org.testng.annotations.Test;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.testng.Assert.assertTrue;

public abstract class DispatcherTestBase {
  public static final String providerName = "DispatcherTestBase main provider";

  public static Object[][] findSubclasses(Class<?> baseClass) {
    Class<?> lookIn = baseClass;
    while (lookIn.getDeclaringClass() != null) {
      lookIn = lookIn.getDeclaringClass();
    }
    return Stream.of(lookIn.getClasses())
      .filter(baseClass::isAssignableFrom)
      .map(c -> new Object[] { c })
      .collect(Collectors.toList())
      .toArray(new Object[0][]);
  }

  protected abstract Dispatcher<?,?,?> getDispatcherUnderTest();

  @Test(dataProvider = providerName)
  public void subclassHasDispatch(Class<?> subclassUnderTest) {
    assertTrue(dispatchBaseClass().isAssignableFrom(subclassUnderTest));
    //noinspection SuspiciousMethodCalls
    assertTrue(getDispatcherUnderTest().dispatches.containsKey(subclassUnderTest), subclassUnderTest.toString());
  }

  private Class<?> dispatchBaseClass() {
    return getDispatcherUnderTest().baseClass;
  }
}

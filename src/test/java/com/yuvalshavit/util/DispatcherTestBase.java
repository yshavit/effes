package com.yuvalshavit.util;

import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

public abstract class DispatcherTestBase {
  public static final String providerName = "DispatcherTestBase main provider";

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

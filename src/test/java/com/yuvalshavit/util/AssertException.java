package com.yuvalshavit.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class AssertException {
  private AssertException() {}

  public static void assertException(Class<? extends Throwable> expected, ExceptionalRunnable action) {
    boolean thrown = true;
    try {
      action.run();
      thrown = false;
    } catch (Throwable e) {
      assertEquals(e.getClass(), expected);
    }
    if (!thrown) {
      fail("Expected exception of type " + expected.getName());
    }
  }

  public interface ExceptionalRunnable {
    public void run() throws Exception;
  }
}

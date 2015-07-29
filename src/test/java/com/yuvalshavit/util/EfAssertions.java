package com.yuvalshavit.util;

import static org.testng.Assert.assertEquals;

import java.util.List;

import com.google.common.base.Joiner;

public class EfAssertions {
  private EfAssertions() {}
  
  public static void equalLists(List<?> actual, List<?> expected) {
    Joiner joiner = Joiner.on('\n');
    assertEquals(joiner.join(actual), joiner.join(expected));
    assertEquals(actual, expected);
  }
}

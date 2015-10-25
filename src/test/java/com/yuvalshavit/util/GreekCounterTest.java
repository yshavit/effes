package com.yuvalshavit.util;

import java.util.Arrays;

import org.testng.annotations.Test;

public class GreekCounterTest {
  @Test
  public void firstFew() {
    GreekCounter counter = new GreekCounter();
    EfAssertions.equalLists(
      Arrays.asList(counter.next(), counter.next(), counter.next()),
      Arrays.asList("α", "β", "γ"));
  }
  
  @Test
  public void wrapToTwoDigits() {
    GreekCounter counter = new GreekCounter();
    for (int i = 0; i < 23; ++i) {
      counter.next();
    }
    EfAssertions.equalLists(
      Arrays.asList(counter.next(), counter.next(), counter.next(), counter.next()),
      Arrays.asList("ω", "αα", "αβ", "αγ"));
  }

  @Test
  public void wrapWithinTwoDigits() {
    GreekCounter counter = new GreekCounter();
    for (int i = 0; i < 47; ++i) {
      counter.next();
    }
    EfAssertions.equalLists(
      Arrays.asList(counter.next(), counter.next(), counter.next(), counter.next()),
      Arrays.asList("αω", "βα", "ββ", "βγ"));
  }

  @Test
  public void wrapToThreeDigits() {
    GreekCounter counter = new GreekCounter();
    for (int i = 0; i < 599; ++i) {
      counter.next();
    }
    EfAssertions.equalLists(
      Arrays.asList(counter.next(), counter.next(), counter.next(), counter.next()),
      Arrays.asList("ωω", "ααα", "ααβ", "ααγ"));
  }
}

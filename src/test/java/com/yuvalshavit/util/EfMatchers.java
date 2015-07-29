package com.yuvalshavit.util;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class EfMatchers {
  private EfMatchers() {}
  
  public static <T extends Lazy<?>> Matcher<T> isUnforced() {
    return new TypeSafeMatcher<T>(Lazy.class) {
      @Override
      public void describeTo(Description description) {
        description.appendText("is unforced");
      }

      @Override
      protected boolean matchesSafely(T item) {
        return item.isUnforced();
      }
    };
  }
}

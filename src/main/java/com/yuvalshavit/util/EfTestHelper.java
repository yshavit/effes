package com.yuvalshavit.util;

public class EfTestHelper {
  private EfTestHelper() {}
  
  public static <T> T safely(SafeSupplier<? extends T> action) {
    try {
      return action.get();
    } catch (Exception | AssertionError e) {
      throw new SafeSupplierException(e);
    }
  }
  
  public interface SafeSupplier<T> {
    T get() throws Exception;
  }

  private static class SafeSupplierException extends RuntimeException {
    private SafeSupplierException(Throwable cause) {
      super(cause);
    }
  }
}

package com.yuvalshavit.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Function;

public final class Util {
  private Util() {}

  @Nullable
  public static <T,R> R elvis(@Nullable T input, @Nonnull Function<T, R> dereferencer) {
    return input != null
      ? dereferencer.apply(input)
      : null;
  }
}

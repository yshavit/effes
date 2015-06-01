package com.yuvalshavit.effes.compile.pmatch;

import java.util.Collection;
import java.util.function.Supplier;

public interface PCaseHandler<T> {
  T whenAny();
  T whenNone();
  T whenSimple(TypedValue value);
  T whenDisjunction(Collection<Supplier<PCase.Simple>> alternatives);
}

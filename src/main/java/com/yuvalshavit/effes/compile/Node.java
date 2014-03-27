package com.yuvalshavit.effes.compile;

abstract class Node {
  @Override
  public abstract String toString(); // force overrides
  public abstract void validate(CompileErrors errs);
}

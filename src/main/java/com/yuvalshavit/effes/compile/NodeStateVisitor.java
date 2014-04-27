package com.yuvalshavit.effes.compile;

public interface NodeStateVisitor {
  void visitChild(Object label, Node child);
  default void visitChild(Node child) {
    visitChild(null, child);
  }
  void visitScalar(Object label, Object value);
}

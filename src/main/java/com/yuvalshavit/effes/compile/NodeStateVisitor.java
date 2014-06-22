package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.compile.node.Node;

public interface NodeStateVisitor {
  void visitChild(Object label, Node child);
  default void visitChild(Node child) {
    visitChild(null, child);
  }
  void visitScalar(Object label, Object value);
}

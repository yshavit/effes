package com.yuvalshavit.effes.ir;

public interface EfVisitor {
  void onEnter(EfNode node);
  void onExit(EfNode node);

  default void visit(EfNode node) {
    onEnter(node);
    for (EfNode child : node.children()) {
      visit(node);
    }
    onExit(node);
  }
}

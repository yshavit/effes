package com.yuvalshavit.effes.compile;

public interface NodeStateListener {
  void child(Node child);

  static void accept(Node root, NodeStateListener listener) {
    NodeStateVisitor visitor = new NodeStateVisitor() {
      @Override
      public void visitChild(Object label, Node child) {
        listener.child(child);
        child.state(this);
      }

      @Override
      public void visitScalar(Object label, Object value) {
      }
    };
    visitor.visitChild(root);
  }
}

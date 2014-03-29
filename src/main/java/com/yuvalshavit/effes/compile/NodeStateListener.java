package com.yuvalshavit.effes.compile;

public interface NodeStateListener {
  void child(Object label, Node child);
  default void child(Node child) {
    child(null, child);
  }
  void scalar(Object label, Object value);
}

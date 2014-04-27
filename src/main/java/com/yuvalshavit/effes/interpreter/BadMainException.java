package com.yuvalshavit.effes.interpreter;

public class BadMainException extends RuntimeException {
  private static final long serialVersionUID = -6027398474550520717L;

  BadMainException(String message) {
    super(message);
  }
}

package com.yuvalshavit.effes.compile;

import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class CompileErrors {

  public static final CompileErrors throwing = null; // TODO make CompileErrors an interface, etc

  private final List<SingleError> errors = new ArrayList<>();

  public void add(Token token, String message) {
    errors.add(new SingleError(token, message));
  }

  public boolean hasErrors() {
    return !errors.isEmpty();
  }

  public Collection<SingleError> getErrors() {
    return Collections.unmodifiableCollection(errors);
  }

  public static class SingleError {
    private final Token token;
    private final String message;

    private SingleError(Token token, String message) {
      this.token = token;
      this.message = message;
    }

    @Override
    public String toString() {
      return formatMessage(token, message);
    }
  }

  private static String formatMessage(Token token, String message) {
    if (token == null) {
      return message;
    }
    String tokenText = token.getText().trim();
    String tokenTextDelim = tokenText.isEmpty()
      ? ""
      : ": ";
    return String.format("%s (at line %d, column %d%s%s)",
      message, token.getLine(), token.getCharPositionInLine() + 1, tokenTextDelim, tokenText);
  }
}

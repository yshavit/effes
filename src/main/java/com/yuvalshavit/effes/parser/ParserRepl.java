package com.yuvalshavit.effes.parser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.TokenSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public final class ParserRepl {
  public static void main(String[] args) throws IOException {
    String ruleName = null;
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    while(true) {
      ruleName = getRuleName(ruleName, reader);
      if (ruleName == null) {
        return;
      }
      String input = getInputBlock(reader);
      if (input == null) {
        return;
      }
      parse(input, ruleName);
    }
  }

  private static String getInputBlock(BufferedReader reader) throws IOException {
    StringBuilder sb = new StringBuilder();
    while (true) {
      String line = reader.readLine();
      if (line == null) {
        return null;
      }
      if (".".equals(line)) {
        return sb.toString();
      }
      sb.append(line).append('\n');
    }
  }

  private static String getRuleName(String defaultValue, BufferedReader reader) throws IOException {
    System.out.print("rule");
    if (defaultValue != null) {
      System.out.printf(" [%s]", defaultValue);
    }
    System.out.print(": ");
    String r = reader.readLine();
    if (r == null) {
      return null;
    }
    r = r.trim();
    if (r.isEmpty()) {
      r = defaultValue != null
        ? defaultValue
        : getRuleName(null, reader);
    }
    System.out.printf("Enter code for %s, with a single \".\" to finish this input.%n", r);
    return r;
  }

  private static void parse(String effes, String ruleName) {
    boolean isFragment;
    if (ruleName.startsWith("_")) {
      ruleName = ruleName.substring(1);
      if (effes.endsWith("\n")) {
        effes = effes.substring(0, effes.length() - 1);
      }
      isFragment = true;
    } else {
      isFragment = false;
    }
    try {
      EffesParser parser = ParserUtils.createParser(effes);
      if (isFragment) {
        TokenSource lexer = parser.getTokenStream().getTokenSource();
        EffesLexer effesLexer = (EffesLexer) lexer;
        effesLexer.getDenterOptions().ignoreEOF();
      }
      ParserRuleContext rule = ParserUtils.ruleByName(parser, ruleName);
      StringBuilder sb = new StringBuilder();
      ParserUtils.prettyPrint(sb, rule, parser);
      System.out.println(sb);
      pause();
      // stopIndex is inclusive, so need to add 1. effes String has trailing \n, so need to subtract 1
      if ((rule.getStop().getStopIndex() + 1) != (effes.length())) {
        System.err.println("Incomplete parse. Parsed the following:");
        String parsed = effes.substring(0, rule.getStop().getStopIndex() + 1);
        for (String parsedLine : parsed.split("\n")) {
          System.err.println("  " + parsedLine);
        }
        pause();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static void pause() {
    try {
      Thread.sleep(10);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }
}

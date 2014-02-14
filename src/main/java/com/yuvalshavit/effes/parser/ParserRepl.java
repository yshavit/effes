package com.yuvalshavit.effes.parser;

import org.antlr.v4.runtime.ParserRuleContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public final class ParserRepl {
  public static void main(String[] args) throws IOException {
    String ruleName = null;
    StringBuilder buffer = new StringBuilder();
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    System.out.print("rule: ");
    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
      if (".".equals(line)) {
        parse(buffer.toString(), ruleName);
        ruleName = null;
        buffer.setLength(0);
        System.out.println();
        System.out.print("rule: ");
      } else if (ruleName == null) {
        ruleName = line;
        System.out.println("Enter code, with a single \".\" to finish this section.");
      } else {
        buffer.append(line).append('\n');
      }
    }
  }

  private static void parse(String effes, String ruleName) {
    try {
      EffesParser parser = ParserUtils.createParser(effes);
      ParserRuleContext rule = ParserUtils.ruleByName(parser, ruleName);
      // stopIndex is inclusive, so need to add 1. effes String has trailing \n, so need to subtract 1
      if ((rule.getStop().getStopIndex() + 1) != (effes.length() - 1)) {
        System.err.println("Incomplete parse. Parsed the following:");
        String parsed = effes.substring(0, rule.getStop().getStopIndex() + 1);
        for (String parsedLine : parsed.split("\n")) {
          System.err.println("  " + parsedLine);
        }
      } else {
        StringBuilder sb = new StringBuilder();
        ParserUtils.prettyPrint(sb, rule, parser);
        System.out.println(sb.toString());
      }
    } catch (IOException e) {
      System.err.println(e);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}

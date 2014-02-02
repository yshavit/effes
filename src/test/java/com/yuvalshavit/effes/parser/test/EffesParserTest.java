package com.yuvalshavit.effes.parser.test;

import com.beust.jcommander.internal.Lists;
import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.yuvalshavit.effes.parser.EffesLexer;
import com.yuvalshavit.effes.parser.EffesParser;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DiagnosticErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.misc.Nullable;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

public final class EffesParserTest {
  private static final String pathBase = EffesParserTest.class.getPackage().getName().replace('.', '/');

  public static final String PARSE_TESTS = "test1";

  @DataProvider(name = "test1")
  public Object[][] readParseFiles() {
    Set<String> files = Sets.newHashSet(readFile("").split("\n"));
    List<Object[]> parseTests = Lists.newArrayList();
    for (String efFile : files) {
      if (efFile.endsWith(".ef")) {
        String plainFile = efFile.substring(0, efFile.length() - ".ef".length());
        parseTests.add(new Object[] { plainFile });
      }
    }
    return parseTests.toArray(new Object[parseTests.size()][]);
  }

  private String readFile(String relativePath) {
    try {
      return Resources.toString(url(relativePath), Charsets.UTF_8);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  @Test(dataProvider = PARSE_TESTS)
  public void parse(String fileNameNoExt) throws IOException, ReflectiveOperationException {
    assertThat("file name", fileNameNoExt, containsString("-"));
    String expectedContent = readFile(fileNameNoExt + ".tree");

    String ruleName = fileNameNoExt.split("-", 2)[0];

    String actualTree;
    try (InputStream efInputStream = new BufferedInputStream(url(fileNameNoExt + ".ef").openStream())) {
      CharStream input = new ANTLRInputStream(efInputStream);
      EffesLexer lexer = new EffesLexer(input);
      lexer.addErrorListener(new AntlrFailureListener());
      TokenStream tokens = new CommonTokenStream(lexer);
      EffesParser parser = new EffesParser(tokens);
      parser.getInterpreter().setPredictionMode(PredictionMode.LL_EXACT_AMBIG_DETECTION);
      parser.removeErrorListeners(); // don't spit to stderr
      parser.addErrorListener(new DiagnosticErrorListener());
      parser.addErrorListener(new AntlrFailureListener());
      Method ruleMethod = parser.getClass().getMethod(ruleName);
      RuleContext rule;
      try {
        rule = (RuleContext) ruleMethod.invoke(parser);
      } catch (InvocationTargetException e) {
        if (e.getCause() instanceof AntlrParseException) {
          throw (AntlrParseException) e.getCause();
        }
        throw e;
      }
      StringBuilder sb = new StringBuilder();
      prettyPrint(sb, 0, rule, parser.getRuleNames(), parser.getTokenNames());
      actualTree = sb.toString();
    }
    assertEquals(actualTree, expectedContent);
  }

  private void prettyPrint(StringBuilder out, int indent, ParseTree tree, String[] ruleNames, String[] tokenNames) {
    assertNotNull(tree);
    assertThat(tree, not(instanceOf(ErrorNode.class)));
    if (tree instanceof RuleNode) {
      writeIndent(out, indent);
      RuleNode rule = (RuleNode) tree;
      String ruleName = ruleNames[rule.getRuleContext().getRuleIndex()];
      out.append(ruleName).append('\n');
      for (int i = 0; i < rule.getChildCount(); ++i) {
        ParseTree child = rule.getChild(i);
        prettyPrint(out, indent + 1, child, ruleNames, tokenNames);
      }
    } else if (tree instanceof TerminalNode) {
      TerminalNode terminal = (TerminalNode) tree;
      String tokenName = tokenNames[terminal.getSymbol().getType()];
      if (tokenIsInteresting(tokenName)) {
        writeIndent(out, indent);
        out.append(tokenName).append(": ").append(terminal.getText()).append('\n');
      }
    } else {
      fail("unrecognized tree class: " + tree.getClass());
    }
  }

  private boolean tokenIsInteresting(String tokenName) {
    // good enough for now; will break if/when the parser doesn't use string literal tokens
    return !tokenName.startsWith("'");
  }

  private void writeIndent(StringBuilder out, int indent) {
    for (int i = 0; i < indent; ++i) {
      out.append("  ");
    }
  }

  private URL url(String fileName) {
    return Resources.getResource(pathBase + "/" + fileName);
  }

  private static class AntlrFailureListener extends BaseErrorListener {
    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, @Nullable Object offendingSymbol, int line,
                            int charPositionInLine, String msg, @Nullable RecognitionException e) {
      throw new AntlrParseException(line, charPositionInLine, msg);
    }
  }

  private static class AntlrParseException extends RuntimeException {
    public AntlrParseException(int line, int posInLine, String msg) {
      // posInLine comes in 0-indexed, but we want to 1-index it so it lines up with what editors say (they
      // tend to 1-index)
      super(String.format("at line %d column %d: %s", line, posInLine+1, msg));
    }
  }
}

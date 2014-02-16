package com.yuvalshavit.effes.parser;

import com.google.common.collect.Sets;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DiagnosticErrorListener;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.misc.Nullable;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

public final class ParserUtils {
  private static final Set<String> tokensToIgnoreOnOutput = Sets.newHashSet("INDENT", "DEDENT", "NL");

  public static EffesParser createParser(Reader input) throws IOException {
    CharStream charStream = new ANTLRInputStream(input);
    EffesLexer lexer = new EffesLexer(charStream);
    lexer.addErrorListener(new ExceptionThrowingFailureListener());
    TokenStream tokens = new CommonTokenStream(lexer);
    EffesParser parser = new EffesParser(tokens);
    parser.getInterpreter().setPredictionMode(PredictionMode.LL_EXACT_AMBIG_DETECTION);
    parser.removeErrorListeners(); // don't spit to stderr
//    parser.setTrace(true);
    parser.addErrorListener(new DiagnosticErrorListener());
    return parser;
  }

  public static EffesParser createParser(String input) throws IOException {
    return createParser(new StringReader(input));
  }

  public static ParserRuleContext ruleByName(EffesParser parser, String ruleName) {
    try {
      Method ruleMethod = parser.getClass().getMethod(ruleName);
      return (ParserRuleContext) ruleMethod.invoke(parser);
    }  catch (InvocationTargetException e) {
      Throwable wrap;
      wrap = (e.getCause() instanceof AntlrParseException)
        ? e.getCause()
        : e;
      throw new RuleInvocationException(wrap);
    } catch (Exception e) {
      throw new RuleInvocationException(e);
    }
  }

  public static void prettyPrint(StringBuilder sb, ParseTree tree, Parser parser) {
    prettyPrint(sb, 0, tree, parser.getRuleNames(), parser.getTokenNames());
  }

  private static void prettyPrint(StringBuilder out, int indent, ParseTree tree, String[] ruleNames, String[] tokenNames) {
    if (tree == null) {
      throw new NullPointerException("null tree");
    }
    if (tree instanceof ErrorNode) {
      out.append("!!!ERROR!!! ").append(tree);
    }
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
      throw new IllegalStateException("unrecognized tree class: " + tree.getClass());
    }
  }

  private static boolean tokenIsInteresting(String tokenName) {
    // good enough for now; will break if/when the parser doesn't use string literal tokens
    return !(tokensToIgnoreOnOutput.contains(tokenName) || tokenName.startsWith("'"));
  }

  private static void writeIndent(StringBuilder out, int indent) {
    for (int i = 0; i < indent; ++i) {
      out.append("  ");
    }
  }

  public static class ExceptionThrowingFailureListener extends BaseErrorListener {
    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, @Nullable Object offendingSymbol, int line,
                            int charPositionInLine, String msg, @Nullable RecognitionException e) {
      throw new AntlrParseException(line, charPositionInLine, msg, e);
    }
  }

  private static class RuleInvocationException extends RuntimeException {
    private RuleInvocationException(Throwable cause) {
      super(cause);
    }
  }
}

package com.yuvalshavit.effes.parser;

import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
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
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.misc.Nullable;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;

public final class ParserUtils {
  public static final String TEST_RULE_SUFFIX = "__ForTest__";
  private static final Set<String> tokensToIgnoreOnOutput = Sets.newHashSet("INDENT", "DEDENT", "NL");

  public static EffesParser createParser(Reader input) throws IOException {
    CharStream charStream = new ANTLRInputStream(input);
    EffesLexer lexer = new EffesLexer(charStream);
    lexer.addErrorListener(new ExceptionThrowingFailureListener());
    TokenStream tokens = new CommonTokenStream(lexer);
    EffesParser parser = new EffesParser(tokens);
//    parser.getInterpreter().setPredictionMode(PredictionMode.LL_EXACT_AMBIG_DETECTION);
    parser.setTrace(Boolean.getBoolean("antlr.trace"));
    if (Boolean.getBoolean("antlr.trace")) {
      parser.setTrace(true);
    } else {
      parser.removeErrorListeners(); // don't spit to stderr
    }
    parser.addErrorListener(new TunableDiagnosticErrorListener());
    return parser;
  }

  public static EffesParser createParser(String input) throws IOException {
    return createParser(new StringReader(input));
  }

  public static ParserRuleContext ruleByName(EffesParser parser, String ruleName) {
    try {
      // try to find ruleName(). If that doesn't exist, find ruleName__ForTest__().
      Method ruleMethod;
      try {
        ruleMethod = parser.getClass().getMethod(ruleName);
      } catch (NoSuchMethodException e) {
        ruleMethod = parser.getClass().getMethod(ruleName + TEST_RULE_SUFFIX);
      }
      return (ParserRuleContext) ruleMethod.invoke(parser);
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof AntlrParseException) {
        throw (AntlrParseException) e.getCause();
      }
      throw new RuleInvocationException(e);
    } catch (Exception e) {
      throw new RuleInvocationException(e);
    }
  }

  public static void prettyPrint(StringBuilder sb, ParseTree tree, Parser parser) {
    prettyPrint(sb, 0, tree, parser.getRuleNames(), parser.getTokenNames());
  }

  public static class LiteralsInvoker {
    private final Set<String> literalTokens;
    private final Set<Method> seenMethods = Sets.newHashSet();

    public LiteralsInvoker(Class<? extends Recognizer<?,?>> recognizerClass) {
      literalTokens = literalTokens(recognizerClass);
    }

    public void invokeLiteralTokenMethods(ParseTree tree) {
      for (Method m : tree.getClass().getDeclaredMethods()) {
        if (!seenMethods.add(m)) {
          continue;
        }
        String name = m.getName();
        if (Character.isUpperCase(name.charAt(0)) && literalTokens.contains(name)) {
          invoke(tree, m);
        }
      }
      for (int i = 0, max = tree.getChildCount(); i < max; ++i) {
        invokeLiteralTokenMethods(tree.getChild(i));
      }
    }

    private void invoke(ParseTree tree, Method m) {
      Class<?>[] params = m.getParameterTypes();
      int nParams = params.length;
      if (nParams == 0) {
        quietInvoke(tree, m);
      } else if (nParams == 1 && params[0] == int.class) {
        quietInvoke(tree, m, 0);
      }
    }

    private static Set<String> literalTokens(Class<?> recognizerClass) {
      Map<Integer, String> intConstsByValue = Maps.newHashMap();
      for (Field field : recognizerClass.getDeclaredFields()) {
        String name = field.getName();
        String rulePrefix = "RULE_";
        int prefixLen = rulePrefix.length();
        if (name.startsWith(rulePrefix) && name.length() > prefixLen && Character.isLowerCase(name.charAt(prefixLen))) {
          continue;
        }
        int m = field.getModifiers();
        if (Modifier.isPublic(m) && Modifier.isStatic(m) && Modifier.isFinal(m) && field.getType() == int.class) {
          Integer value;
          try {
            value = field.getInt(null);
          } catch (IllegalAccessException e) {
            throw new AssertionError(e);
          }
          if (intConstsByValue.containsKey(value)) {
            intConstsByValue.put(value, null);
          } else {
            intConstsByValue.put(value, name);
          }
        }
      }
      return ImmutableSet.copyOf(Collections2.filter(intConstsByValue.values(), Predicates.notNull()));
    }
  }

  private static Object quietInvoke(ParseTree tree, Method m, Object... args) {
    try {
      return m.invoke(tree, args);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void prettyPrint(StringBuilder out, int indent, ParseTree tree, String[] ruleNames, String[] tokenNames) {
    if (tree == null) {
      throw new NullPointerException("null tree");
    }
    if (tree instanceof ErrorNode) {
      out.append("!!!ERROR!!! ").append(tree);
    }
    if (tree instanceof RuleNode) {
      RuleNode rule = (RuleNode) tree;
      String ruleName = ruleNames[rule.getRuleContext().getRuleIndex()];
      if (ruleName.endsWith(TEST_RULE_SUFFIX) && rule.getChildCount() == 1) {
        --indent; // so that it stays at the same indent level when we recurse with indent + 1
      } else {
        StringBuilder expectedClassName = new StringBuilder(ruleName).append("Context");
        expectedClassName.setCharAt(0, Character.toUpperCase(expectedClassName.charAt(0)));
        String actualClassName = tree.getClass().getSimpleName();
        if (!expectedClassName.toString().equals(actualClassName)) {
          StringBuilder labelName = new StringBuilder(actualClassName);
          labelName.setLength(labelName.length() - "Context".length());
          // This is a labeled alternative.
          ruleName = labelName.toString();
        }
        writeIndent(out, indent);
        out.append(ruleName).append('\n');
      }
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

  public static class TunableDiagnosticErrorListener extends DiagnosticErrorListener {
    @Override
    public void reportAttemptingFullContext(@NotNull Parser recognizer, @NotNull DFA dfa, int startIndex, int stopIndex,
                                            @NotNull ATNConfigSet configs) {
      if (Boolean.getBoolean("antlr.report.ALL")) {
        super.reportAttemptingFullContext(recognizer, dfa, startIndex, stopIndex, configs);
      }
    }

    @Override
    public void reportContextSensitivity(@NotNull Parser recognizer, @NotNull DFA dfa, int startIndex, int stopIndex,
                                         @NotNull ATNConfigSet configs) {
      if (Boolean.getBoolean("antlr.report.contextSensitivity")) {
        super.reportContextSensitivity(recognizer, dfa, startIndex, stopIndex, configs);
      }
    }
  }

  public static class ExceptionThrowingFailureListener extends BaseErrorListener {
    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, @Nullable Object offendingSymbol, int line,
                            int charPositionInLine, String msg, @Nullable RecognitionException e) {
      if (recognizer instanceof EffesParser) {
        EffesParser parser = (EffesParser) recognizer;
        ParserRuleContext rule = parser.getRuleContext();
        msg = String.format("%s at \"%s\" (rule '%s')", msg, rule.getText(), parser.getRuleNames()[rule.getRuleIndex()]);
      }
      throw new AntlrParseException(line, charPositionInLine, msg, e);
    }
  }

  private static class RuleInvocationException extends RuntimeException {
    private RuleInvocationException(Throwable cause) {
      super(cause);
    }
  }
}

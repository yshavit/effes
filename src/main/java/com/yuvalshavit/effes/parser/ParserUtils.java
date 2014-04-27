package com.yuvalshavit.effes.parser;

import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
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
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class ParserUtils {
  public static final String TEST_RULE_SUFFIX = "__ForTest__";
  private static final Set<String> tokensToIgnoreOnOutput = Sets.newHashSet("INDENT", "DEDENT", "NL");

  public static EffesParser createParser(Reader input) throws IOException {
    EffesLexer lexer = createLexer(input);
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

  public static EffesLexer createLexer(Reader input) throws IOException {
    CharStream charStream = new ANTLRInputStream(input);
    EffesLexer lexer = new EffesLexer(charStream);
    lexer.addErrorListener(new ExceptionThrowingFailureListener());
    return lexer;
  }

  public static EffesParser createParser(String input) {
    try {
      return createParser(new StringReader(input));
    } catch (IOException e) {
      throw new RuntimeException("unexpected IOException while parsing String", e);
    }
  }

  public static EffesParser createParser(String... lines) {
    return createParser(Joiner.on('\n').join(lines));
  }

  public static void walk(EffesListener listener, EffesParser.CompilationUnitContext source) {
    new ParseTreeWalker().walk(listener, source);
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

  public static <R extends ParserRuleContext> R parseRule(Class<R> ruleClass, String code) {
    StringBuilder ruleName = new StringBuilder(ruleClass.getSimpleName());
    ruleName.setLength(ruleName.length() - "Context".length());
    ruleName.setCharAt(0, Character.toLowerCase(ruleName.charAt(0)));
    return ruleClass.cast(ruleByName(ParserUtils.createParser(code), ruleName.toString()));
  }

  public static String prettyPrint(ParseTree tree) {
    return prettyPrint(tree, new EffesParser(null));
  }

  public static String prettyPrint(ParseTree tree, Parser parser) {
    StringBuilder sb = new StringBuilder();
    prettyPrint(sb, tree, parser);
    return sb.toString();
  }

  public static void prettyPrint(StringBuilder sb, ParseTree tree, Parser parser) {
    prettyPrint(sb, 0, tree, null, parser.getRuleNames(), parser.getTokenNames());
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
      } else if (nParams == 1 && int.class.equals(params[0])) {
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
        if (Modifier.isPublic(m) && Modifier.isStatic(m) && Modifier.isFinal(m) && int.class.equals(field.getType())) {
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

  private static void quietInvoke(ParseTree tree, Method m, Object... args) {
    try {
      m.invoke(tree, args);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void prettyPrint(StringBuilder out, int indent, ParseTree tree, String label, String[] ruleNames, String[] tokenNames) {
    if (tree == null) {
      throw new NullPointerException("null tree");
    }
    if (tree instanceof ErrorNode) {
      out.append("!!!ERROR!!! ").append(tree).append('\n');
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
        if (label != null) {
          out.append(label).append("= ");
        }
        out.append(ruleName).append('\n');
      }

      Map<ParseTree, String> labelsByChild = getLabelsByChildren(tree);
      for (int i = 0; i < rule.getChildCount(); ++i) {
        ParseTree child = rule.getChild(i);
        prettyPrint(out, indent + 1, child, labelsByChild.get(child), ruleNames, tokenNames);
      }
    } else if (tree instanceof TerminalNode) {
      TerminalNode terminal = (TerminalNode) tree;
      String tokenName = tokenName(tokenNames, terminal.getSymbol().getType());
      if (tokenIsInteresting(tokenName)) {
        writeIndent(out, indent);
        out.append(tokenName).append(": ").append(terminal.getText()).append('\n');
      }
    } else {
      throw new IllegalStateException("unrecognized tree class: " + tree.getClass());
    }
  }

  private static Map<ParseTree, String> getLabelsByChildren(ParseTree tree) {
    Map<ParseTree, String> labelsByContent = Maps.newHashMap();
    for (Field f : tree.getClass().getDeclaredFields()) {
      int mod = f.getModifiers();
      if (Modifier.isPublic(mod)
        && !Modifier.isStatic(mod)
        && !Modifier.isFinal(mod)
        && fieldHoldsChildren(f))
      {
        try {
          Object value = f.get(tree);
          List<ParseTree> children;
          String label = f.getName();
          if (value instanceof List<?>) {
            label += "[]";
            List<?> list = (List<?>) value;
            children = Lists.newArrayListWithCapacity(list.size());
            children.addAll(list.stream().map(o -> (ParseTree) o).collect(Collectors.toList()));
          } else {
            children = Lists.newArrayList((ParseTree) value);
          }
          for (ParseTree child : children) {
            String name = labelsByContent.get(child);
            name = (name != null)
              ? name + "," + label
              : label;
            labelsByContent.put(child, name);
          }
        } catch (IllegalAccessException e) {
          throw new AssertionError(e);
        }
      }
    }
    return labelsByContent;
  }

  private static boolean fieldHoldsChildren(Field f) {
    if (ParserRuleContext.class.isAssignableFrom(f.getType())) {
      return true;
    }
    Type genericType = f.getGenericType();
    if (genericType instanceof ParameterizedType) {
      ParameterizedType paramType = (ParameterizedType) genericType;
      Type[] params = paramType.getActualTypeArguments();
      if (List.class.equals(paramType.getRawType())
        && params.length == 1
        && (params[0] instanceof Class<?>)
        && ParserRuleContext.class.isAssignableFrom((Class<?>) params[0])) {
        return true;
      }
    }
    return false;
  }

  public static String tokenName(String[] tokenNames, int tokenType) {
    return tokenType == -1 ? "EOF" : tokenNames[tokenType];
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
                                            @Nullable BitSet conflictingAlts, @NotNull ATNConfigSet configs) {

      if (Boolean.getBoolean("antlr.report.ALL")) {
        super.reportAttemptingFullContext(recognizer, dfa, startIndex, stopIndex, conflictingAlts, configs);
      }
    }

    @Override
    public void reportContextSensitivity(@NotNull Parser recognizer, @NotNull DFA dfa, int startIndex, int stopIndex,
                                         int prediction, @NotNull ATNConfigSet configs) {
      if (Boolean.getBoolean("antlr.report.contextSensitivity")) {
        super.reportContextSensitivity(recognizer, dfa, startIndex, stopIndex, prediction, configs);
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
    private static final long serialVersionUID = -5543180428138631983L;

    private RuleInvocationException(Throwable cause) {
      super(cause);
    }
  }
}

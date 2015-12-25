package com.yuvalshavit.effes.compile;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Pair;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.google.common.base.Functions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.yuvalshavit.effes.compile.node.*;
import com.yuvalshavit.effes.compile.pmatch.PAlternative;
import com.yuvalshavit.effes.compile.pmatch.PAlternativeSubtractionResult;
import com.yuvalshavit.effes.compile.pmatch.PPossibility;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.util.Dispatcher;
import com.yuvalshavit.util.EfCollections;

public final class ExpressionCompiler {
  private final MethodsRegistry<?> methodsRegistry;
  private final MethodsRegistry<?> builtInMethods;
  private final TypeRegistry typeRegistry;
  private final CompileErrors errs;
  private final Scopes<EfVar,Token> vars;
  private EfType.SimpleType declaringType;

  public ExpressionCompiler(
    MethodsRegistry<?> methodsRegistry,
    MethodsRegistry<?> builtInMethods,
    TypeRegistry typeRegistry,
    CompileErrors errs,
    Scopes<EfVar,Token> vars)
  {
    this.methodsRegistry = methodsRegistry;
    this.builtInMethods = builtInMethods;
    this.typeRegistry = typeRegistry;
    this.errs = errs;
    this.vars = vars;
  }

  public void setDeclaringType(EfType.SimpleType type) {
    this.declaringType = type;
  }

  public Expression apply(EffesParser.ExprContext exprContext) {
    return dispatcher.apply(this, exprContext);
  }

  public Expression apply(EffesParser.ExprLineContext exprLineContext) {
    return exprLineDispatcher.apply(this, exprLineContext);
  }

  private static final Dispatcher<ExpressionCompiler,EffesParser.ExprContext,Expression> dispatcher =
    Dispatcher.builder(ExpressionCompiler.class, EffesParser.ExprContext.class, Expression.class)
              .put(EffesParser.InstanceMethodInvokeOrVarExprContext.class, ExpressionCompiler::instanceMethodInvokeOrvar)
              .put(EffesParser.MethodInvokeOrVarExprContext.class, ExpressionCompiler::methodInvokeOrVar)
              .put(EffesParser.ParenExprContext.class, ExpressionCompiler::paren)
              .put(EffesParser.CtorInvokeContext.class, ExpressionCompiler::ctorInvoke)
              .put(EffesParser.IntLiteralExprContext.class, ExpressionCompiler::intLiteralExpr)
              .build(ExpressionCompiler::error);

  private Expression error(EffesParser.ExprContext ctx) {
    errs.add(ctx.getStart(), "unrecognized expression");
    return new Expression.UnrecognizedExpression(ctx.getStart());
  }

  private Expression declaringObject(Token token) {
    assert declaringType != null;
    return new Expression.VarExpression(token, EfVar.arg(EfVar.THIS_VAR_NAME, 0, declaringType.reify(t -> t)));
  }

  private Expression intLiteralExpr(EffesParser.IntLiteralExprContext ctx) {
    Token tok = ctx.getStart();
    long v;
    try {
      v = Long.valueOf(ctx.getText());
    } catch (NumberFormatException e) {
      errs.add(tok, "invalid int literal (must fit in 64-bit signed int)");
      return new Expression.UnrecognizedExpression(tok);
    }
    return new Expression.IntLiteral(tok, v);
  }

  private Expression methodInvokeOrVar(EffesParser.MethodInvokeOrVarExprContext ctx) {
    EffesParser.MethodInvokeContext methodInvoke = ctx.methodInvoke();
    // If this method is a VAR_NAME with no args, it could be an instance arg or a var. That takes precedence.
    if (couldBeVar(methodInvoke)) {
      TerminalNode varNode = methodInvoke.methodName().VAR_NAME();
      String varName = varNode.getText();
      if (declaringType != null) {
        CtorArg arg = declaringType.getArgByName(varName);
        if (arg != null) {
          return new Expression.InstanceArg(ctx.getStart(), declaringObject(ctx.getStart()), arg);
        }
      }
      EfVar var = vars.get(varName);
      if (var != null) {
        return varExpr(var, varNode);
      }
    }
    return methodInvoke(ctx.methodInvoke(), true, null);
  }

  private Expression instanceMethodInvokeOrvar(EffesParser.InstanceMethodInvokeOrVarExprContext ctx) {
    Expression target = apply(ctx.expr());
    EffesParser.MethodInvokeContext methodInvoke = ctx.methodInvoke();
    return methodInvoke(methodInvoke, true, target);
  }

  private static boolean couldBeVar(EffesParser.MethodInvokeContext methodInvoke) {
    return methodInvoke.methodInvokeArgs().expr().isEmpty() && methodInvoke.methodName().VAR_NAME() != null;
  }

  public Expression methodInvoke(
    EffesParser.MethodInvokeContext ctx,
    boolean usedAsExpression,
    Expression target)
  {
    if (target == null || target.resultType() instanceof EfType.SimpleType) {
      EfType.SimpleType lookOn = target != null
        ? (EfType.SimpleType) target.resultType()
        : null;
      return getMethodInvokeOnSimpleTarget(ctx, usedAsExpression, target, lookOn);
    } else if (target.resultType() instanceof EfType.DisjunctiveType) {
      return vars.inScope(() -> {
        EfVar matchAgainstVar = tryGetEfVar(target);
        Expression caseOf;
        Expression inMatcher;
        if (matchAgainstVar == null) {
          matchAgainstVar = EfVar.var(vars.uniqueName(), vars.countElems(), target.resultType());
          vars.add(matchAgainstVar, target.token());
          caseOf = new Expression.AssignExpression(target, matchAgainstVar);
          inMatcher = new Expression.VarExpression(target.token(), matchAgainstVar);
        } else {
          caseOf = target;
          inMatcher = target;
        }

        EfType.DisjunctiveType targetDisjunction = (EfType.DisjunctiveType) target.resultType();

        List<CaseConstruct.Alternative<Expression>> alternatives = targetDisjunction.getAlternatives()
                                                                                    .stream()
                                                                                    .sorted(EfType.comparator) // just for ease of debugging, provide consistent ordering
                                                                                    .map((EfType efType) -> {
                                                                                      if (!(efType instanceof EfType.SimpleType)) {
                                                                                        errs.add(
                                                                                          inMatcher.token(),
                                                                                          "unrecognized type (possibly a compiler error): " + efType);
                                                                                        return null;
                                                                                      }
                                                                                      EfType.SimpleType matchAgainstType = (EfType.SimpleType) efType;
                                                                                      PAlternative typeMatcher = matchAllArgsFor(matchAgainstType);
                                                                                      Expression downcast = new Expression.CastExpression(
                                                                                        inMatcher,
                                                                                        matchAgainstType);
                                                                                      Expression downcastMethodInvoke = methodInvoke(
                                                                                        ctx,
                                                                                        usedAsExpression,
                                                                                        downcast);
                                                                                      return new CaseConstruct.Alternative<>(typeMatcher, downcastMethodInvoke);
                                                                                    })
                                                                                    .filter(Objects::nonNull).collect(Collectors.toList());
        return new Expression.CaseExpression(target.token(), new CaseConstruct<>(caseOf, alternatives));
      });
    } else {
      errs.add(target.token(), "unrecognized type for target of method invocation");
      return new Expression.UnrecognizedExpression(target.token());
    }
  }

  private Expression getMethodInvokeOnSimpleTarget(
    EffesParser.MethodInvokeContext ctx,
    boolean usedAsExpression,
    Expression target,
    EfType.SimpleType lookOn)
  {
    String methodName = ctx.methodName().getText();
    List<EffesParser.TypeContext> explicitGenericsCtx = ctx.singleTypeParameters().type();
    MethodLookup methodLookup = lookUp(methodName, lookOn);
    if (methodLookup == null) {
      if (couldBeVar(ctx)) {
        EfVar var = vars.get(methodName);
        if (var != null) {
          return new Expression.VarExpression(ctx.getStart(), var);
        } else if (target != null) {
          CtorArg arg = lookOn.getArgByName(methodName);
          if (arg == null) {
            errs.add(ctx.methodName().getStart(), "no such method or variable: '" + methodName + '\'');
          } else {
            return new Expression.InstanceArg(ctx.getStart(), target, arg);
          }
        } else {
          // couldn't find var or method, and it's not an instance so there's no instance arg
          errs.add(ctx.methodName().getStart(), "no such method or variable: '" + methodName + '\'');
        }
      } else {
        errs.add(ctx.methodName().getStart(), "no such method: '" + methodName + '\'');
      }
    }

    List<Expression> invokeArgs = ctx.methodInvokeArgs().expr().stream().map(this::apply).collect(Collectors.toList());
    if (target != null) {
      List<Expression> invokeArgsWithTarget = new ArrayList<>(invokeArgs.size() + 1);
      invokeArgsWithTarget.add(target);
      invokeArgsWithTarget.addAll(invokeArgs);
      invokeArgs = invokeArgsWithTarget;
    }
    if (methodLookup == null) {
      // The following won't actually matter; it's only used for the executable, but this is a compile error
      return new Expression.UnrecognizedExpression(ctx.getStart(), invokeArgs);
    }
    if (target == null && methodLookup.id.getDefinedOn() != null) {
      invokeArgs.add(0, Expression.thisExpression(ctx.methodInvokeArgs().getStart(), methodLookup.id.getDefinedOn()));
    }
    if (!explicitGenericsCtx.isEmpty()) {
      TypeResolver typeResolver = new TypeResolver(typeRegistry, errs, null); // TODO move to ctor, hook up to current type context
      List<EfType> explicitGenerics = explicitGenericsCtx.stream().map(typeResolver::apply).collect(Collectors.toList());
      int nExplicitGenerics = explicitGenerics.size();
      int nExpectedGenerics = methodLookup.method.getGenerics().size();
      if (nExplicitGenerics > nExpectedGenerics) {
        errs.add(explicitGenericsCtx.get(nExplicitGenerics).getStart(), "too many generics provided");
      }
      int nGenerics = Math.min(nExplicitGenerics, nExpectedGenerics);
      Map<EfType.GenericType,EfType> genericsMapping = new HashMap<>(nGenerics);
      for (int i = 0; i < nGenerics; ++i) {
        genericsMapping.put(methodLookup.method.getGenerics().get(i), explicitGenerics.get(i));
      }
      Function<EfType.GenericType,EfType> genericsLookup = Functions.forMap(genericsMapping, EfType.UNKNOWN)::apply;
      methodLookup = methodLookup.reify(genericsLookup);
    } else if (!methodLookup.method.getGenerics().isEmpty()) {
      Map<EfType.GenericType,EfType> inferredGenerics = new HashMap<>();
      List<EfType> declaredTypes = methodLookup.method.getArgs().viewTypes();
      List<EfType> invokeTypes = invokeArgs.stream().map(Expression::resultType).collect(Collectors.toList());
      for (int i = 0, len = Math.min(declaredTypes.size(), invokeTypes.size()); i < len; ++i) {
        infer(methodLookup.method.getTrueId(), declaredTypes.get(i), invokeTypes.get(i), inferredGenerics);
      }
      methodLookup = methodLookup.reify(g -> inferredGenerics.getOrDefault(g, EfType.UNKNOWN));
    }
    return new Expression.MethodInvoke(
      ctx.getStart(),
      methodLookup.id,
      methodLookup.method,
      invokeArgs,
      methodLookup.isBuiltIn,
      usedAsExpression);
  }

  private void infer(EfMethod.Id trueId, EfType declared, EfType actual, Map<EfType.GenericType,EfType> inferredGenerics) {
    // TODO need to improve this!
    if (declared instanceof EfType.GenericType) {
      EfType.GenericType generic = (EfType.GenericType) declared;
      if (trueId.equals(generic.getOwner())) {
        inferredGenerics.put(generic, actual);
      }
    }
  }

  private MethodLookup lookUp(String methodName, EfType.SimpleType lookOn) {
    // If we had method specialization, it would probably be done by prefixing more-specific scopes.
    Stream<EfType.SimpleType> scopes;
    if (lookOn != null) {
      scopes = lookOn.equals(declaringType)
        ? Stream.of(declaringType, null)
        : Stream.of(lookOn, declaringType, null);
    } else if (declaringType != null) {
      scopes = Stream.of(declaringType, null);
    } else {
      scopes = Stream.of(new EfType.SimpleType[] {null});
    }

    for (MethodId scope : scopes.map(s -> MethodId.of(s, methodName)).collect(Collectors.toList())) {
      EfMethod<?> m = methodsRegistry.getMethod(scope);
      if (m != null) {
        if (lookOn != null && lookOn.getGeneric().equals(scope.getDefinedOn())) {
          m = m.reify(lookOn.getReification());
        }
        return new MethodLookup(scope, m, false);
      }
      m = builtInMethods.getMethod(scope);
      if (m != null) {
        return new MethodLookup(scope, m, true);
      }
    }
    return null;
  }

  private Expression paren(EffesParser.ParenExprContext ctx) {
    return dispatcher.apply(this, ctx.expr());
  }

  private Expression ctorInvoke(EffesParser.CtorInvokeContext ctx) {
    String typeName = ctx.TYPE_NAME().getText();
    List<EfType.GenericType> typeParams = typeRegistry.getSimpleTypeParams(typeName);
    final EfType.SimpleType type;
    final List<Expression> args;
    final List<CtorArg> ctorArgs;
    if (typeParams == null) {
      errs.add(ctx.getStart(), "unknown type: " + typeName);
      type = null;
      args = ImmutableList.of();
      ctorArgs = ImmutableList.of();
    } else {
      args = ctx.expr().stream().map(this::apply).collect(Collectors.toList());

      List<EffesParser.TypeContext> ctorGenericParams = ctx.singleTypeParameters().type();
      if (ctorGenericParams == null) {
        ctorGenericParams = Collections.emptyList();
      }
      TypeResolver typeResolver = new TypeResolver(typeRegistry, errs, null); // TODO move to ctor, hook up to current type context

      Token start = ctx.singleTypeParameters().getStart();
      Function<EfType.GenericType,EfType> reification = typeResolver.getReification(start, typeParams, ctorGenericParams);
      type = typeResolver.getSimpleType(typeName, reification);
      // TODO type might be null?
      //    List<EfVar> args = argsByType.get(type.getGeneric());
      //    Preconditions.checkArgument(args != null, "unknown type: " + type);
      //    return args.stream().map(v -> v.reify(reification)).collect(Collectors.toList());
      ctorArgs = type.getArgs();
    }
    return new Expression.CtorInvoke(ctx.getStart(), type, ctorArgs, args);
  }

  private Expression varExpr(EfVar var, TerminalNode name) {
    return new Expression.VarExpression(name.getSymbol(), var);
  }

  private static final Dispatcher<ExpressionCompiler,EffesParser.ExprLineContext,Expression> exprLineDispatcher
    = Dispatcher.builder(ExpressionCompiler.class, EffesParser.ExprLineContext.class, Expression.class)
                .put(EffesParser.SingleLineExpressionContext.class, ExpressionCompiler::singleLine)
                .put(EffesParser.CaseExpressionContext.class, ExpressionCompiler::caseExpression)
                .build(ExpressionCompiler::exprLineErr);

  private Expression singleLine(EffesParser.SingleLineExpressionContext ctx) {
    return apply(ctx.expr());
  }

  private Expression caseExpression(EffesParser.CaseExpressionContext ctx) {
    Expression matchAgainst = apply(ctx.expr());
    EfVar matchAgainstVar = tryGetEfVar(matchAgainst);
    if (matchAgainst.resultType().equals(EfType.UNKNOWN) || matchAgainst.resultType().equals(EfType.VOID)) {
      errs.add(ctx.expr().getStart(), "case expressions require a disjunctive target type, but the target's type is unknown");
      return new Expression.UnrecognizedExpression(ctx.expr().getStart());
    }

    List<CaseConstruct.Alternative<Expression>> patterns = new ArrayList<>(ctx.caseAlternative().size());
    PAlternativeSubtractionResult subtractionResult = new PAlternativeSubtractionResult(matchAgainst.resultType());
    for (EffesParser.CaseAlternativeContext alternativeCtx : ctx.caseAlternative()) {
      CasePatternCompilation compilation = casePattern(alternativeCtx.casePattern());
      PAlternative alternative;
      if (compilation == null) {
        errs.add(alternativeCtx.casePattern().getStart(), "invalid case pattern");
        alternative = PAlternative.any().build();
      } else {
        alternative = compilation.pAlternative;
      }
      PAlternativeSubtractionResult nextPossibility = alternative.subtractFrom(subtractionResult);
      if (nextPossibility == null) {
        String msg = (subtractionResult.remainingPossibility() == PPossibility.none)
          ? String.format("%s can never match", alternative)
          : String.format("%s can't match against %s", alternative, subtractionResult.remainingResultType());
        errs.add(ctx.getStart(), msg);
        continue;
      } else {
        subtractionResult = nextPossibility;
      }
      boolean createScope = (matchAgainstVar != null) || subtractionResult.bindings().size() > 0;
      if (createScope) {
        vars.pushScope();
      }
      if (matchAgainstVar != null) {
        EfVar matchAgainstDowncast = matchAgainstVar.cast(subtractionResult.matchedType());
        vars.replace(matchAgainstDowncast);
      }
      subtractionResult.bindings().forEach((varName, varType) -> {
        Iterator<Token> tokens = compilation.getTokens(varName).iterator();
        Token tok;
        if (!tokens.hasNext()) {
          tok = alternativeCtx.getStart();
        } else {
          tok = tokens.next();
          while (tokens.hasNext()) {
            Token dupeToken = tokens.next();
            errs.add(dupeToken, String.format("duplicate variable name '%s'", varName));
          }
        }
        vars.add(EfVar.var(varName, vars.countElems(), varType), tok);
      });
      Expression ifMatched;
      if (alternativeCtx.exprBlock() == null) {
        errs.add(alternativeCtx.exprBlock().getStart(), "couldn't parse block for case pattern");
        ifMatched = new Expression.UnrecognizedExpression(alternativeCtx.getStart());
      } else {
        ifMatched = apply(alternativeCtx.exprBlock().expr());
      }
      if (createScope) {
        vars.popScope();
      }
      CaseConstruct.Alternative<Expression> caseAlt = new CaseConstruct.Alternative<>(alternative, ifMatched);
      patterns.add(caseAlt);
    }
    checkAllAlternativesMatched(ctx, subtractionResult);
    CaseConstruct<Expression> construct = new CaseConstruct<>(matchAgainst, patterns);
    return new Expression.CaseExpression(ctx.getStart(), construct);
  }

  public void checkAllAlternativesMatched(ParserRuleContext ctx, PAlternativeSubtractionResult subtractionResult) {
    if (!subtractionResult.remainingPossibility().equals(PPossibility.none)) {
      errs.add(ctx.getStart(), "unmatched possibility: " + subtractionResult.remainingResultType());
    }
  }

  @Nullable
  public EfVar tryGetEfVar(Expression expression) {
    EfVar matchAgainstVar;
    if (expression instanceof Expression.VarExpression) {
      Expression.VarExpression varExpr = (Expression.VarExpression) expression;
      matchAgainstVar = varExpr.getVar();
    } else {
      matchAgainstVar = null;
    }
    return matchAgainstVar;
  }

  private Expression exprLineErr(EffesParser.ExprLineContext ctx) {
    return new Expression.UnrecognizedExpression(ctx.getStart());
  }

  private static final Dispatcher<ExpressionCompiler,EffesParser.CasePatternContext,CasePatternCompilation> casePatternDispatcher
    = Dispatcher.builder(ExpressionCompiler.class, EffesParser.CasePatternContext.class, CasePatternCompilation.class)
                .put(EffesParser.SingleTypePatternMatchContext.class, ExpressionCompiler::casePattern)
                .put(EffesParser.IntLiteralPatternMatchContext.class, ExpressionCompiler::casePattern)
                .put(EffesParser.VarBindingPatternMatchContext.class, ExpressionCompiler::casePattern)
                .put(EffesParser.UnboundWildPatternMatchContext.class, ExpressionCompiler::casePattern)
                .build(ExpressionCompiler::casePatternErr);

  public CasePatternCompilation casePattern(EffesParser.CasePatternContext casePattern) {
    return casePatternDispatcher.apply(this, casePattern);
  }

  private static class CasePatternCompilation {
    @Nullable
    final PAlternative pAlternative;
    Multimap<String,Token> wildcardTokens = null;

    public CasePatternCompilation(PAlternative pAlternative) {
      this.pAlternative = checkNotNull(pAlternative, "pAlternative");
    }

    public void addTokens(Multimap<String,Token> tokens) {
      if (tokens != null && !tokens.isEmpty()) {
        getOrCreateTokensMultimap().putAll(tokens);
      }
    }

    public void addToken(String varName, Token token) {
      getOrCreateTokensMultimap().put(varName, token);
    }

    public Collection<Token> getTokens(String varName) {
      return wildcardTokens == null ? Collections.emptySet() : wildcardTokens.get(varName);
    }

    private Multimap<String,Token> getOrCreateTokensMultimap() {
      if (wildcardTokens == null) {
        wildcardTokens = ArrayListMultimap.create();
      }
      return wildcardTokens;
    }
  }

  @Nullable
  private CasePatternCompilation casePattern(EffesParser.SingleTypePatternMatchContext casePattern) {
    TerminalNode typeNameTok = casePattern.TYPE_NAME();
    String typeName = typeNameTok.getText();
    EfType.SimpleType type = typeRegistry.getSimpleType(typeName);
    if (type == null) {
      errs.add(typeNameTok.getSymbol(), "unknown type: " + typeName);
      return null;
    }
    List<EffesParser.CasePatternContext> providedArgPatterns = casePattern.casePattern();
    if (providedArgPatterns.size() != type.getArgs().size()) {
      Token tok = providedArgPatterns.size() > type.getArgs().size() ? providedArgPatterns.get(type.getArgs().size()).getStart() : casePattern.getStop();
      errs.add(
        tok,
        String.format("expected %d argument%s, but saw %d", type.getArgs().size(), (type.getArgs().size() == 1 ? "" : "s"), providedArgPatterns.size()));
      return new CasePatternCompilation(matchAllArgsFor(type));
    }
    List<CasePatternCompilation> argCompilations = providedArgPatterns.stream().map(this::casePattern).collect(Collectors.toList());
    PAlternative pAlternative = PAlternative.simple(type, argCompilations.stream().map(c -> c.pAlternative).toArray(PAlternative[]::new));
    CasePatternCompilation result = new CasePatternCompilation(pAlternative);
    argCompilations.forEach(c -> result.addTokens(c.wildcardTokens));
    return result;
  }

  private CasePatternCompilation casePattern(EffesParser.IntLiteralPatternMatchContext casePattern) {
    long literalValue;
    try {
      literalValue = Long.parseLong(casePattern.getText());
    } catch (NumberFormatException e) {
      errs.add(casePattern.getStart(), "number out of range: " + casePattern.getText());
      return null;
    }
    EfType.SimpleType type = literalValue == 0
      ? BuiltinType.IntZero.getEfType()
      : BuiltinType.IntValue.getEfType();
    return new CasePatternCompilation(PAlternative.simple(type, new PAlternative[0])); // TODO using an empty array to resolve the overload is uuuuuugly
  }

  private CasePatternCompilation casePattern(EffesParser.VarBindingPatternMatchContext casePattern) {
    TerminalNode varNode = casePattern.VAR_NAME();
    String varName = varNode.getText();
    CasePatternCompilation compilation = new CasePatternCompilation(PAlternative.any(varName).build());
    compilation.addToken(varName, varNode.getSymbol());
    return compilation;
  }

  private CasePatternCompilation casePattern(EffesParser.UnboundWildPatternMatchContext casePattern) {
    return new CasePatternCompilation(PAlternative.any().build());
  }

  private CasePatternCompilation casePatternErr(EffesParser.CasePatternContext casePattern) {
    Class<?> clazz;
    Token tok;
    if (casePattern == null) {
      clazz = null;
      tok = null;
    } else {
      clazz = casePattern.getClass();
      tok = casePattern.getStart();
    }
    errs.add(tok, "unknown error in handling pattern of type " + clazz);
    return null;
  }

  /**
   * @param ctx the alternative's AST
   * @param matchAgainstType the type of the target of the "case of", which may affect the EfType element of the result (e.g. if it's a wildcard match)
   * @return a pair whose first element is the PAlternative compiled from the ctx, and whose second element is the EfType of the match, or <tt>null</tt> if
   * there was an error
   */
  public Pair<PAlternative,EfType> compilePattern(EffesParser.CasePatternContext ctx, EfType matchAgainstType) {
    if (ctx instanceof EffesParser.SingleTypePatternMatchContext) {
      EffesParser.SingleTypePatternMatchContext simpleTypePattern = (EffesParser.SingleTypePatternMatchContext) ctx;
      String typeName = simpleTypePattern.TYPE_NAME().getText();
      List<EffesParser.CasePatternContext> args = simpleTypePattern.casePattern();
      EfType.SimpleType type = typeRegistry.getSimpleType(typeName);
      if (type == null) {
        errs.add(simpleTypePattern.TYPE_NAME().getSymbol(), "unrecognized type: " + typeName);
        return null;
      }
      if (type.getArgs().size() != args.size()) {
        String plural = type.getArgs().size() == 1 ? "s" : "";
        errs.add(ctx.getStart(), String.format("%s should take %d argument%s, but saw %d", typeName, type.getArgs().size(), plural, args.size()));
        return null;
      }
      Set<EfType.SimpleType> viableOptions = matchAgainstType.simpleTypes().stream().filter(type::equals).collect(Collectors.toSet());
      PAlternative[] argAlts = new PAlternative[args.size()];
      EfType[] argTypes = new EfType[args.size()];
      for (int i = 0; i < args.size(); ++i) {
        int argIdx = i;
        EfType argType = viableOptions.stream().map(t -> t.getArgs().get(argIdx).type()).collect(EfCollections.efTypeCollector());
        Pair<PAlternative,EfType> altCompiled = compilePattern(args.get(i), argType);
        if (altCompiled == null) {
          // TODO confirm that a message was logged already
          argAlts[i] = PAlternative.any().build(); // TODO not a great option, should really be "none"
          argTypes[i] = EfType.UNKNOWN;
        } else {
          argAlts[i] = altCompiled.a;
          argTypes[i] = altCompiled.b;
        }
      }
      PAlternative resultAlt = PAlternative.simple(type, argAlts);
      EfType.SimpleType resultType = type.withCtorArgs(argTypes);
      return new Pair<>(resultAlt, resultType);
    } else if (ctx instanceof EffesParser.VarBindingPatternMatchContext) {
      String varName = ((EffesParser.VarBindingPatternMatchContext) ctx).VAR_NAME().getText();
      return new Pair<>(PAlternative.any(varName).build(), matchAgainstType);
    } else if (ctx instanceof EffesParser.UnboundWildPatternMatchContext) {
      return new Pair<>(PAlternative.any().build(), matchAgainstType);
    } else if (ctx instanceof EffesParser.IntLiteralPatternMatchContext) {
      TerminalNode intNode = ((EffesParser.IntLiteralPatternMatchContext) ctx).INT();
      long longValue;
      try {
        longValue = Long.parseLong(intNode.getText());
      } catch (NumberFormatException e) {
        errs.add(intNode.getSymbol(), "number out of range");
        longValue = Long.MIN_VALUE;
      }
      if (longValue == 0) {
        EfType.SimpleType zeroType = BuiltinType.IntZero.getEfType();
        if (!matchAgainstType.contains(zeroType)) {
          return null;
        }
        return new Pair<>(PAlternative.simple(zeroType, new PAlternative[0]), zeroType); // TODO using an empty arg to resolve the overload is uuuuuugly
      } else {
        EfType.SimpleType intType = BuiltinType.IntValue.getEfType();
        if (!matchAgainstType.contains(intType)) {
          return null;
        }
        throw new UnsupportedOperationException("how do I use this number?"); // TODO
      }
    } else {
      errs.add(ctx.getStart(), "compiler error: unhandled " + ctx.getClass().getSimpleName());
      return null;
    }
  }

  private static PAlternative matchAllArgsFor(EfType.SimpleType matchAgainstType) {
    PAlternative[] wildArgs = new PAlternative[matchAgainstType.getArgs().size()];
    for (int i = 0; i < wildArgs.length; ++i) {
      wildArgs[i] = PAlternative.any().build();
    }
    return PAlternative.simple(matchAgainstType, wildArgs);
  }

  private static class MethodLookup {
    private final MethodId id;
    private final EfMethod<?> method;
    private final boolean isBuiltIn;

    private MethodLookup(MethodId id, EfMethod<?> method, boolean isBuiltIn) {
      this.id = id;
      this.method = method;
      this.isBuiltIn = isBuiltIn;
    }

    public MethodLookup reify(Function<EfType.GenericType,EfType> reification) {
      return new MethodLookup(id, method.reify(reification), isBuiltIn);
    }

    @Override
    public String toString() {
      return id + " " + method;
    }
  }
}

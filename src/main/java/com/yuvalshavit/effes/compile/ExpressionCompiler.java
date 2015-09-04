package com.yuvalshavit.effes.compile;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.yuvalshavit.effes.compile.node.*;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.util.Dispatcher;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Pair;
import org.antlr.v4.runtime.tree.TerminalNode;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ExpressionCompiler {
  private final MethodsRegistry<?> methodsRegistry;
  private final MethodsRegistry<?> builtInMethods;
  private final TypeRegistry typeRegistry;
  private final CompileErrors errs;
  private final Scopes<EfVar, Token> vars;
  private EfType.SimpleType declaringType;

  public ExpressionCompiler(MethodsRegistry<?> methodsRegistry,
                            MethodsRegistry<?> builtInMethods,
                            TypeRegistry typeRegistry,
                            CompileErrors errs,
                            Scopes<EfVar, Token> vars)
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

  private static final Dispatcher<ExpressionCompiler, EffesParser.ExprContext, Expression> dispatcher =
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
        Function<EfType.GenericType, EfType> reification = t -> t;
        CtorArg arg = declaringType.getArgByName(varName, reification);
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

  public Expression methodInvoke(EffesParser.MethodInvokeContext ctx,
                                 boolean usedAsExpression,
                                 Expression target) {
    if (target == null || target.resultType() instanceof EfType.SimpleType) {
      EfType.SimpleType lookOn = target != null
        ? (EfType.SimpleType) target.resultType()
        : null;
      return getMethodInvokeOnSimpleTarget(ctx, usedAsExpression, target, lookOn);
    } else if (target.resultType() instanceof EfType.DisjunctiveType) {
      return vars.inScope( () -> {
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

        EfVar matchAgainstVarClosure = matchAgainstVar;
        List<CaseConstruct.Alternative<Expression>> alternatives = targetDisjunction.getAlternatives()
          .stream()
          .sorted(EfType.comparator) // just for ease of debugging, provide consistent ordering
          .map((EfType efType) -> {
            if (!(efType instanceof EfType.SimpleType)) {
              errs.add(inMatcher.token(), "unrecognized type (possibly a compiler error): " + efType);
              return null;
            }
            EfType.SimpleType matchAgainstType = (EfType.SimpleType) efType;
            CasePattern casePattern = new CasePattern(matchAgainstType, ImmutableList.of(), inMatcher.token());
            Expression downcast = new Expression.CastExpression(inMatcher, matchAgainstType);
            return this.<Void, Expression>caseAlternative( // gotta help the type inference a bit
              null,
              matchAgainstVarClosure,
              ignored1 -> casePattern,
              ignored2 -> getMethodInvokeOnSimpleTarget(ctx, usedAsExpression, downcast, matchAgainstType)
            );
          })
          .filter(Objects::nonNull).collect(Collectors.toList());
        return new Expression.CaseExpression(target.token(), new CaseConstruct<>(caseOf, alternatives));
      });
    } else {
      errs.add(target.token(), "unrecognized type for target of method invocation");
      return new Expression.UnrecognizedExpression(target.token());
    }
  }

  private Expression getMethodInvokeOnSimpleTarget(EffesParser.MethodInvokeContext ctx,
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
        }
        else if (target != null) {
          CtorArg arg = lookOn.getArgByName(methodName, lookOn.getReification());
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
      Map<EfType.GenericType, EfType> genericsMapping = new HashMap<>(nGenerics);
      for (int i = 0; i < nGenerics; ++i) {
        genericsMapping.put(methodLookup.method.getGenerics().get(i), explicitGenerics.get(i));
      }
      Function<EfType.GenericType, EfType> genericsLookup = Functions.forMap(genericsMapping, EfType.UNKNOWN)::apply;
      methodLookup = methodLookup.reify(genericsLookup);
    } else if (!methodLookup.method.getGenerics().isEmpty()) {
      Map<EfType.GenericType, EfType> inferredGenerics = new HashMap<>();
      List<EfType> declaredTypes = methodLookup.method.getArgs().viewTypes();
      List<EfType> invokeTypes = invokeArgs.stream().map(Expression::resultType).collect(Collectors.toList());
      for (int i = 0, len = Math.min(declaredTypes.size(), invokeTypes.size()); i < len; ++i) {
        infer(methodLookup.method.getTrueId(), declaredTypes.get(i), invokeTypes.get(i), inferredGenerics);
      }
      methodLookup = methodLookup.reify(g -> inferredGenerics.getOrDefault(g, EfType.UNKNOWN));
    }
    return new Expression.MethodInvoke(ctx.getStart(),
                                       methodLookup.id,
                                       methodLookup.method,
                                       invokeArgs,
                                       methodLookup.isBuiltIn,
                                       usedAsExpression);
  }

  private void infer(EfMethod.Id trueId, EfType declared, EfType actual, Map<EfType.GenericType, EfType> inferredGenerics) {
    // TODO need to improve this!
    if(declared instanceof EfType.GenericType) {
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
      scopes = Stream.of(new EfType.SimpleType[] { null });
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

  private static final Dispatcher<ExpressionCompiler, EffesParser.ExprLineContext, Expression> exprLineDispatcher
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
    List<CaseConstruct.Alternative<Expression>> patterns = ctx.caseAlternative().stream()
      .map(c -> caseAlternative(c, matchAgainstVar))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
    CaseConstruct<Expression> construct = new CaseConstruct<>(matchAgainst, patterns);
    return new Expression.CaseExpression(ctx.getStart(), construct);
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

  @Nullable
  private CaseConstruct.Alternative<Expression> caseAlternative(EffesParser.CaseAlternativeContext ctx,
                                                                @Nullable EfVar matchAgainst) {
    return caseAlternative(
      ctx,
      matchAgainst,
      t-> casePattern(t.casePattern()),
      c -> c.exprBlock() != null
        ? apply(c.exprBlock().expr())
        : new Expression.UnrecognizedExpression(c.getStart()));
  }

  private static final Dispatcher<ExpressionCompiler, EffesParser.CasePatternContext, CasePattern> casePatternDispatcher
    = Dispatcher.builder(ExpressionCompiler.class, EffesParser.CasePatternContext.class, CasePattern.class)
    .put(EffesParser.SingleTypePatternMatchContext.class, ExpressionCompiler::casePattern)
    .put(EffesParser.IntLiteralPatternMatchContext.class, ExpressionCompiler::casePattern)
    .build(ExpressionCompiler::casePatternErr);
    
  
  public CasePattern casePattern(EffesParser.CasePatternContext casePattern) {
    return casePatternDispatcher.apply(this, casePattern);
  }
  
  private CasePattern casePattern(EffesParser.SingleTypePatternMatchContext casePattern) {
    TerminalNode tok = casePattern.TYPE_NAME();
    TypeResolver resolver = new TypeResolver(typeRegistry, errs, declaringType);
    EfType.SimpleType matchType = resolver.getSimpleType(tok, casePattern.singleTypeParameters().type());
    if (matchType == null) {
      errs.add(tok.getSymbol(), String.format("unrecognized type '%s' for pattern matcher", tok.getText()));
      return null;
    }
    matchType = matchType.reify(g -> {
      errs.add(tok.getSymbol(), "can't have generic types in case statements");
      return EfType.UNKNOWN;
    });

    List<Pair<Token, String>> bindings
      = casePattern.VAR_NAME().stream().map(t -> new Pair<>(t.getSymbol(), t.getText())).collect(Collectors.toList());

    return new CasePattern(matchType, bindings, casePattern.getStart());
  }
  
  private CasePattern casePattern(EffesParser.IntLiteralPatternMatchContext casePattern) {
    long literalValue;
    try {
      literalValue = Long.parseLong(casePattern.getText());
    } catch (NumberFormatException e) {
      errs.add(casePattern.getStart(), "number out of range: " + casePattern.getText());
      return null;
    }
    if (literalValue == 0) {
      return new CasePattern(BuiltinType.IntZero.getEfType(), Collections.emptyList(), casePattern.getStart());
    } else {
      errs.add(casePattern.getStart(), "non-0 int literals are unsupported as pattern matchers");
      return null;
    }
  }

  private CasePattern casePatternErr(EffesParser.CasePatternContext casePattern) {
    Class<?> clazz;
    Token tok;
    if (casePattern == null) {
      clazz = null;
      tok = null;
    } else {
      clazz = casePattern.getClass();
      tok = casePattern.getStart();
    }
    errs.add(tok, String.format("unknown error in handling pattern of type " + clazz));
    return null;
  }
  
  @Nullable
  public <C, N extends Node> CaseConstruct.Alternative<N> caseAlternative(
    C ctx,
    @Nullable EfVar matchAgainst,
    Function<C, CasePattern> patternExtractor,
    Function<C, N> ifMatchesExtractor)
  {
    CasePattern casePattern = patternExtractor.apply(ctx);
    if (casePattern == null) {
      return null;
    }
    EfType.SimpleType matchType = casePattern.matchType();
    List<Pair<Token, String>> bindingTokens = casePattern.bindings();
    //    List<EfVar> args = argsByType.get(type.getGeneric());
    //    Preconditions.checkArgument(args != null, "unknown type: " + type);
    //    return args.stream().map(v -> v.reify(reification)).collect(Collectors.toList());
    List<CtorArg> matchtypeArgs = matchType.getArgs();
    vars.pushScope();
    if (matchAgainst != null) {
      EfVar matchAgainstDowncast = matchAgainst.cast(matchType);
      vars.replace(matchAgainstDowncast);
    }
    List<EfVar> bindingArgs = new ArrayList<>(bindingTokens.size());
    for (int i = 0; i < bindingTokens.size(); ++i) {
      Pair<Token, String> bindingToken = bindingTokens.get(i);
      String bindingName = bindingToken.b;
      EfType bindingType = i < matchtypeArgs.size()
        ? matchtypeArgs.get(i).type()
        : EfType.UNKNOWN;
      EfVar binding = EfVar.var(bindingName, vars.countElems(), bindingType);
      vars.add(binding, bindingToken.a);
      bindingArgs.add(binding);
    }
    N ifMatches = ifMatchesExtractor.apply(ctx);
    vars.popScope();
    if (ifMatches == null) {
      errs.add(casePattern.token(), "unrecognized alternative for pattern " + matchType);
    }
    return new CaseConstruct.Alternative<>(matchType, matchtypeArgs, bindingArgs, ifMatches);
  }

  public static class CasePattern {
    private final EfType.SimpleType matchType;
    private final List<Pair<Token, String>> bindings;
    private final Token token;

    public CasePattern(EfType.SimpleType matchType, List<Pair<Token, String>> bindings, Token token) {
      this.matchType = matchType;
      this.bindings = ImmutableList.copyOf(bindings);
      this.token = token;
    }

    public EfType.SimpleType matchType() {
      return matchType;
    }

    public List<Pair<Token, String>> bindings() {
      return bindings;
    }

    public Token token() {
      return token;
    }
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

    public MethodLookup reify(Function<EfType.GenericType, EfType> reification) {
      return new MethodLookup(id, method.reify(reification), isBuiltIn);
    }

    @Override
    public String toString() {
      return id + " " + method;
    }
  }
}

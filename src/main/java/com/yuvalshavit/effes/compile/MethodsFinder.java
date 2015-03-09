package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.compile.node.CompileErrors;
import com.yuvalshavit.effes.compile.node.EfArgs;
import com.yuvalshavit.effes.compile.node.EfMethod;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.effes.compile.node.EfVar;
import com.yuvalshavit.effes.compile.node.MethodId;
import com.yuvalshavit.effes.parser.EffesBaseListener;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.*;
import java.util.function.Consumer;

public final class MethodsFinder implements Consumer<Sources> {

  private final MethodsRegistry<EffesParser.InlinableBlockContext> methodsRegistry;
  private final CompileErrors errs;
  private final TypeRegistry typeRegistry;
  private final CtorRegistry ctors;

  public MethodsFinder(TypeRegistry typeRegistry,
                       MethodsRegistry<EffesParser.InlinableBlockContext> methodsRegistry,
                       CtorRegistry ctors,
                       CompileErrors errs) {
    this.typeRegistry = typeRegistry;
    this.methodsRegistry = methodsRegistry;
    this.ctors = ctors;
    this.errs = errs;
  }

  @Override
  public void accept(Sources sources) {
    MethodsInfo info = new MethodsInfo();
    sources.forEach(source -> ParserUtils.walk(new Finder(info), source.getParseUnit()));
    sources.forEach(source -> ParserUtils.walk(new Verifier(source.isBuiltin()), source.getParseUnit()));
    checkOpenMethods(info);
  }

  private void checkOpenMethods(MethodsInfo info) {
    for (Map.Entry<? extends MethodId, ? extends EfMethod<?>> entry : info.openMethods.getMethodsByName().entrySet()) {
      MethodId methodId = entry.getKey();
      String methodName = methodId.getName();
      EfMethod<?> method = entry.getValue();
      assert methodId.getDefinedOn() != null : methodId;
      String openTypeName = methodId.getDefinedOn().getName(); // TODO generics?!
      EfType openType = typeRegistry.getType(openTypeName);
      assert openType != null : methodId;
      openType.simpleTypes().forEach(t -> {
        MethodId tId = MethodId.of(t, methodName);
        isSubmethod(methodId, method, tId, methodsRegistry.getMethod(tId), info);
      });
    }
  }

  /**
   * Checks if <tt>toCheck</tt> is a "submethod" of <tt>prototype</tt>. That's true iff it has the same number of
   * arguments, each argument is a supertype of the corresponding method in prototype, and the result type is a subtype
   * of prototype's result.
   * @param prototype the "super" method
   * @param toCheck the "sub" method, possibly
   * @param methodsInfo lots of info about the methods, and specifically their tokens
   */
  private void isSubmethod(MethodId prototypeId, EfMethod<?> prototype, MethodId toCheckId, EfMethod<?> toCheck,
                           MethodsInfo methodsInfo)
  {
    int nArgsProto = prototype.getArgs().length();
    int nArgsToCheck = toCheck.getArgs().length();
    int nArgsBoth;
    if (nArgsProto == nArgsToCheck) {
      nArgsBoth = nArgsProto;
    } else {
      String plural = nArgsToCheck == 1
        ? ""
        : "s";
      errs.add(methodsInfo.argsStart(toCheckId),
               String.format("%s takes %d argument%s, but it should take %d because of %s",
                             toCheckId, nArgsToCheck, plural, nArgsProto, prototypeId));
      nArgsBoth = Math.min(nArgsProto, nArgsToCheck);
    }

    List<EfArgs.Arg> protoArgs = prototype.getArgs().asList();
    List<EfArgs.Arg> toCheckArgs = toCheck.getArgs().asList();
    for (int i = 0; i < nArgsBoth; ++i) {
      EfArgs.Arg protoArg = protoArgs.get(i);
      EfArgs.Arg toCheckArg = toCheckArgs.get(i);
      if (EfVar.THIS_VAR_NAME.equals(protoArg.name()) && EfVar.THIS_VAR_NAME.equals(toCheckArg.name())) {
        // Args are allowed to be "wrong" in this case. For instance, Boolean::negate's first argument is a Boolean,
        // which expands to (True | False). True::negate's first argument is True (not True | False), but we should
        // allow this because we assume that callers will only invoke True::negate on a True object, so that arg
        // is always valid.
        continue;
      }
      // e.g. if proto has an arg (True | False), then toCheck's arg can be (True | False | Void) but not
      // (True). toCheck must be able to accept any args that proto accepts.
      if (!toCheckArg.type().contains(protoArg.type())) {
        errs.add(methodsInfo.getArgToken(toCheckId, toCheckArg.name()),
                 String.format("in %s, %s is %s but must be %s because of %s",
                               toCheckId, toCheckArg.name(), toCheckArg.type(), protoArg.type(), prototypeId));
      }
    }

    if (!prototype.getResultType().contains(toCheck.getResultType())) {
      errs.add(methodsInfo.resultTypeToken(toCheckId),
               String.format("%s returns %s but must return %s because of %s",
                             toCheckId, toCheck.getResultType(), prototype.getResultType(), prototypeId));
    }
  }

  private class Finder extends EffesBaseListener {

    private final MethodsInfo methodsInfo;
    private EfType.SimpleType declaringType = null;
    private String declaringOpenType = null;

    Finder(MethodsInfo methodsInfo) {
      this.methodsInfo = methodsInfo;
    }
    
    @Override
    public void enterDataTypeDeclr(@NotNull EffesParser.DataTypeDeclrContext ctx) {
      enterCheck();
      declaringType = new TypeResolver(typeRegistry, errs, null).getSimpleType(ctx.TYPE_NAME().getText(), EfType.KEEP_GENERIC);
      if (declaringType == null) {
        // This shouldn't happen, since the typeResolver should have already picked up all declared types, and this
        // method only gets called when the listener sees a type declaration!
        errs.add(ctx.TYPE_NAME().getSymbol(), String.format("unrecognized type '%s' (this is likely a compiler bug)",
          ctx.TYPE_NAME().getText()));
      } else {
        declaringType = declaringType.reify(t -> t);
      }
    }

    @Override
    public void exitDataTypeDeclr(@NotNull EffesParser.DataTypeDeclrContext ctx) {
      declaringType = null;
    }

    @Override
    public void enterOpenTypeDeclr(@NotNull EffesParser.OpenTypeDeclrContext ctx) {
      enterCheck();
      declaringOpenType = ctx.TYPE_NAME().getText();
    }

    @Override
    public void exitOpenTypeDeclr(@NotNull EffesParser.OpenTypeDeclrContext ctx) {
      assert declaringOpenType != null;
      declaringOpenType = null;
    }

    @Override
    public void enterMethodDeclr(@NotNull EffesParser.MethodDeclrContext ctx) {
      String name = ctx.methodName().getText();
      if (declaringType != null && ctors.getArgByName(declaringType, name, t -> t) != null) {
        errs.add(ctx.methodName().getStart(), "can't declare method that hides a data type arg");
      }

      EfMethod.Id id = new EfMethod.Id(name);
      EfArgs.Builder args = new EfArgs.Builder(errs);
      MethodTokens methodTokens = new MethodTokens();

      if (declaringType != null) {
        args.add(ctx.getStart(), EfVar.THIS_VAR_NAME, declaringType);
      } else if (declaringOpenType != null) {
        EfType type = typeRegistry.getType(declaringOpenType);
        assert type != null : declaringOpenType;
        args.add(ctx.getStart(), EfVar.THIS_VAR_NAME, type);
      }
      methodTokens.argsStart = ctx.methodArgs().getStart();
      TypeResolver typeResolver = new TypeResolver(typeRegistry, errs, declaringType);
      for (EffesParser.MethodArgContext argContext : ctx.methodArgs().methodArg()) {
        EffesParser.TypeContext typeContext = argContext.type();
        if (typeContext != null) {
          // if it's null, there was a parse error that'll be handled
          EfType type = typeResolver.apply(typeContext); // TODO handle generic in a way that handles declared generics as well as context type
          String argName = Optional.ofNullable(argContext.VAR_NAME()).map(ParseTree::getText).orElseGet(() -> null);
          args.add(typeContext.getStart(), argName, type);
          methodTokens.argTokens.put(argName, argContext.getStart());
        }
      }
      final EfType resultType;
      EffesParser.TypeContext resultTypeContext = ctx.methodReturnDeclr().type();
      if (resultTypeContext != null) {
        if (resultTypeContext.singleType().isEmpty()) {
          errs.add(resultTypeContext.getStart(), "no result type provided after arrow");
          resultType = EfType.UNKNOWN;
        } else {
          resultType = typeResolver.apply(resultTypeContext);
          methodTokens.resultTypeStart = resultTypeContext.getStart();
        }
      } else {
        resultType = EfType.VOID;
        methodTokens.resultTypeStart = ctx.methodReturnDeclr().getStart();
      }
      EffesParser.InlinableBlockContext body = ctx.methodBody().inlinableBlock();
      List<EfType.GenericType> generics = Collections.emptyList(); // TODO; needs to account for method's genericsDeclr as well as context type!
      EfMethod<EffesParser.InlinableBlockContext> method = new EfMethod<>(id, generics, args.build(), resultType, body);
      MethodId methodId;
      if (declaringOpenType != null) {
        assert declaringType == null : declaringType;
        // TODO emptyList will have to change when open types can be parameterized
        EfType.SimpleType openSimple = new EfType.SimpleType(declaringOpenType, Collections.emptyList());
        methodId = MethodId.of(openSimple, name);
        methodsInfo.openMethods.registerMethod(methodId, method);
      } else {
        methodId = MethodId.of(declaringType, name);
        if (body == null) {
          // TODO actually register it from the builtins registry? validation against expected args, etc?
        } else {
          try {
            methodsRegistry.registerMethod(methodId, method);
          } catch (DuplicateMethodNameException e) {
            errs.add(ctx.methodName().getStart(), e.getMessage());
          }
        }
      }
      methodsInfo.register(methodId, methodTokens);
    }

    @Override
    public void visitErrorNode(@NotNull ErrorNode node) {
      errs.add(node.getSymbol(), "parse error");
    }

    private void enterCheck() {
      if (declaringType != null) {
        throw new IllegalStateException("already have declaring type context: " + declaringType);
      }
      if (declaringOpenType != null) {
        throw new IllegalStateException("already have declaring type context: " + declaringOpenType);
      }
    }
  }

  /**
   * Everything you want to know about methods we find!
   */
  private static class MethodsInfo {
    private static final MethodTokens blankInfos = new MethodTokens();
    private final MethodsRegistry<Object> openMethods = new MethodsRegistry<>();
    private final Map<MethodId, MethodTokens> methodsTokens = new HashMap<>();

    public void register(MethodId methodId, MethodTokens tokens) {
      if (!methodsTokens.containsKey(methodId)) {
        // if it already contains this method, it's a dupe. Other code will complain about that; for here, assume
        // the first one
        methodsTokens.put(methodId, tokens);
      }
    }

    public Token argsStart(MethodId methodId) {
      return methodsTokens.getOrDefault(methodId, blankInfos).argsStart;
    }

    public Token getArgToken(MethodId methodId, String argName) {
      return methodsTokens.getOrDefault(methodId, blankInfos).argTokens.get(argName);
    }

    public Token resultTypeToken(MethodId methodId) {
      return methodsTokens.getOrDefault(methodId, blankInfos).resultTypeStart;
    }
  }

  private static class MethodTokens {
    private final Map<String, Token> argTokens = new HashMap<>();
    private Token argsStart;
    private Token resultTypeStart;
  }

  private enum VerifierMode {
    DATA_TYPE, OPEN_TYPE, NONE
  }

  private class Verifier extends EffesBaseListener {

    private final boolean sourceIsBuiltin;

    public Verifier(boolean sourceIsBuiltin) {
      this.sourceIsBuiltin = sourceIsBuiltin;
    }

    private VerifierMode mode = VerifierMode.NONE;

    @Override
    public void enterDataTypeDeclr(@NotNull EffesParser.DataTypeDeclrContext ctx) {
      checkModeNone(ctx.getStart());
      mode = VerifierMode.DATA_TYPE;
    }

    @Override
    public void exitDataTypeDeclr(@NotNull EffesParser.DataTypeDeclrContext ctx) {
      if (mode != VerifierMode.DATA_TYPE) {
        errs.add(ctx.getStart(), "expected DATA_TYPE mode (this is a compiler bug)");
      }
      mode = VerifierMode.NONE;
    }

    @Override
    public void enterOpenTypeDeclr(@NotNull EffesParser.OpenTypeDeclrContext ctx) {
      checkModeNone(ctx.getStart());
      mode = VerifierMode.OPEN_TYPE;
    }

    @Override
    public void exitOpenTypeDeclr(@NotNull EffesParser.OpenTypeDeclrContext ctx) {
      if (mode != VerifierMode.OPEN_TYPE) {
        errs.add(ctx.getStart(), "expected OPEN_TYPE mode (this is a compiler bug)");
      }
      mode = VerifierMode.NONE;
    }

    @Override
    public void enterMethodBody(@NotNull EffesParser.MethodBodyContext ctx) {
      switch (mode) {
      case NONE:
      case DATA_TYPE:
        if (ctx.inlinableBlock() == null) {
          if (ctx.BUILTIN() == null) {
            errs.add(ctx.getStop(), "expected method body");
          } else {
            if (!sourceIsBuiltin) {
              errs.add(ctx.BUILTIN().getSymbol(), "can't declare built-in methods in userland source code");
            }
          }
        }
        break;
      case OPEN_TYPE:
        if (ctx.inlinableBlock() != null) {
          errs.add(ctx.inlinableBlock().getStart(), "methods in open types can't have bodies");
        }
        break;
      default:
        errs.add(ctx.getStart(), "unrecognized mode " + mode + " (this is a compiler bug)");
        break;
      }
    }

    private void checkModeNone(Token token) {
      if (mode != VerifierMode.NONE) {
        errs.add(token, "unrecognized mode " + mode + " (this is a compiler bug)");
      }
    }
  }
}

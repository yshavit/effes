package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.parser.EffesBaseListener;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public final class MethodsFinder implements Consumer<EffesParser.CompilationUnitContext> {

  private final TypeResolver typeResolver;
  private final MethodsRegistry<EffesParser.InlinableBlockContext> methodsRegistry;
  private final CompileErrors errs;
  private final TypeRegistry typeRegistry;

  public MethodsFinder(TypeRegistry typeRegistry,
                       MethodsRegistry<EffesParser.InlinableBlockContext> methodsRegistry,
                       CompileErrors errs) {
    this.typeRegistry = typeRegistry;
    this.typeResolver = new TypeResolver(typeRegistry, errs);
    this.methodsRegistry = methodsRegistry;
    this.errs = errs;
  }

  @Override
  public void accept(EffesParser.CompilationUnitContext source) {
    MethodsRegistry<?> openMethods = ParserUtils.walk(new Finder(), source).openMethods;
    ParserUtils.walk(new Verifier(), source);
    checkOpenMethods(openMethods);
  }

  private void checkOpenMethods(MethodsRegistry<?> openMethods) {
    for (Map.Entry<? extends MethodId, ? extends EfMethod<?>> entry : openMethods.getMethodsByName().entrySet()) {
      MethodId methodId = entry.getKey();
      String methodName = methodId.getName();
      EfMethod<?> method = entry.getValue();
      assert methodId.getDefinedOn() != null : methodId;
      String openTypeName = methodId.getDefinedOn().getName();
      EfType openType = typeRegistry.getType(openTypeName);
      assert openType != null : methodId;
      openType.simpleTypes().forEach(t -> {
        MethodId tId = MethodId.of(t, methodName);
        isSubmethod(methodId, method, tId, methodsRegistry.getMethod(tId));
      });
    }
  }

  @Deprecated
  private static Token UNKNOWN = null; // TODO
  /**
   * Checks if <tt>toCheck</tt> is a "submethod" of <tt>prototype</tt>. That's true iff it has the same number of
   * arguments, each argument is a supertype of the corresponding method in prototype, and the result type is a subtype
   * of prototype's result.
   * @param prototype the "super" method
   * @param toCheck the "sub" method, possibly
   */
  private void isSubmethod(MethodId prototypeId, EfMethod<?> prototype, MethodId toCheckId, EfMethod<?> toCheck) {
    int nArgsProto = prototype.getArgs().length();
    int nArgsToCheck = toCheck.getArgs().length();
    int nArgsBoth;
    if (nArgsProto == nArgsToCheck) {
      nArgsBoth = nArgsProto;
    } else {
      String plural = nArgsToCheck == 1
        ? ""
        : "s";
      errs.add(UNKNOWN, String.format("%s takes %d argument%s, but it should take %d because of %s",
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
        errs.add(UNKNOWN,
                 String.format("in %s, %s is %s but must be %s because of %s",
                               toCheckId, toCheckArg.name(), toCheckArg.type(), protoArg.type(), prototypeId));
      }
    }

    if (!prototype.getResultType().contains(toCheck.getResultType())) {
      errs.add(UNKNOWN, String.format("%s returns %s but must return %s because of %s",
                                      toCheckId, toCheck.getResultType(), prototype.getResultType(), prototypeId));
    }
  }

  private class Finder extends EffesBaseListener {

    private final MethodsRegistry<Object> openMethods = new MethodsRegistry<>();
    private EfType.SimpleType declaringType = null;
    private String declaringOpenType = null;

    @Override
    public void enterDataTypeDeclr(@NotNull EffesParser.DataTypeDeclrContext ctx) {
      enterCheck();
      declaringType = typeResolver.getSimpleType(ctx.TYPE_NAME().getText());
      if (declaringType == null) {
        // This shouldn't happen, since the typeResolver should have already picked up all declared types, and this
        // method only gets called when the listener sees a type declaration!
        errs.add(ctx.TYPE_NAME().getSymbol(), String.format("unrecognized type '%s' (this is likely a compiler bug)",
          ctx.TYPE_NAME().getText()));
      }
    }

    @Override
    public void exitDataTypeDeclr(@NotNull EffesParser.DataTypeDeclrContext ctx) {
      assert declaringType != null;
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
      if (declaringType != null && declaringType.getArgByName(name) != null) {
        errs.add(ctx.methodName().getStart(), "can't declare method that hides a data type arg");
      }
      EfArgs.Builder args = new EfArgs.Builder(errs);

      if (declaringType != null) {
        args.add(ctx.getStart(), EfVar.THIS_VAR_NAME, declaringType);
      } else if (declaringOpenType != null) {
        EfType type = typeRegistry.getType(declaringOpenType);
        assert type != null : declaringOpenType;
        args.add(ctx.getStart(), EfVar.THIS_VAR_NAME, type);
      }
      for (EffesParser.MethodArgContext argContext : ctx.methodArgs().methodArg()) {
        EffesParser.TypeContext typeContext = argContext.type();
        if (typeContext != null) {
          // if it's null, there was a parse error that'll be handled
          EfType type = typeResolver.apply(typeContext);
          String argName = Optional.ofNullable(argContext.VAR_NAME()).map(TerminalNode::getText).orElseGet(() -> null);
          args.add(typeContext.getStart(), argName, type);
        }
      }
      EfType resultType = ctx.methodReturnDeclr().type() != null
        ? typeResolver.apply(ctx.methodReturnDeclr().type())
        : EfType.VOID;
      EffesParser.InlinableBlockContext body = ctx.inlinableBlock();
      EfMethod<EffesParser.InlinableBlockContext> method = new EfMethod<>(args.build(), resultType, body);
      if (declaringOpenType != null) {
        assert declaringType == null : declaringType;
        EfType.SimpleType openSimple = new EfType.SimpleType(declaringOpenType);
        openMethods.registerMethod(MethodId.of(openSimple, name), method);
      } else {
        try {
          methodsRegistry.registerMethod(MethodId.of(declaringType, name), method);
        } catch (DuplicateMethodNameException e) {
          errs.add(ctx.methodName().getStart(), e.getMessage());
        }
      }
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

  private enum VerifierMode {
    DATA_TYPE, OPEN_TYPE, NONE
  }

  private class Verifier extends EffesBaseListener {

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
    public void enterMethodDeclr(@NotNull EffesParser.MethodDeclrContext ctx) {
      switch (mode) {
      case NONE:
      case DATA_TYPE:
        if (ctx.inlinableBlock() == null) {
          errs.add(ctx.getStop(), "expected method body");
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

package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.parser.EffesBaseListener;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.Optional;
import java.util.function.Consumer;

public final class MethodsFinder implements Consumer<EffesParser.CompilationUnitContext> {

  private final TypeResolver typeResolver;
  private final MethodsRegistry<EffesParser.InlinableBlockContext> methodsRegistry;
  private final CompileErrors errs;

  public MethodsFinder(TypeRegistry typeRegistry,
                       MethodsRegistry<EffesParser.InlinableBlockContext> methodsRegistry,
                       CompileErrors errs) {
    this.typeResolver = new TypeResolver(typeRegistry, errs);
    this.methodsRegistry = methodsRegistry;
    this.errs = errs;
  }

  @Override
  public void accept(EffesParser.CompilationUnitContext source) {
    ParserUtils.walk(new Finder(), source);
    ParserUtils.walk(new Verifier(), source);
  }

  private class Finder extends EffesBaseListener {

    private EfType.SimpleType declaringType = null;

    @Override
    public void enterDataTypeDeclr(@NotNull EffesParser.DataTypeDeclrContext ctx) {
      if (declaringType != null) {
        throw new IllegalStateException("already have declaring type context: " + declaringType);
      }
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
    public void enterMethodDeclr(@NotNull EffesParser.MethodDeclrContext ctx) {
      String name = ctx.methodName().getText();
      if (declaringType != null && declaringType.getArgByName(name) != null) {
        errs.add(ctx.methodName().getStart(), "can't declare method that hides a data type arg");
      }
      EfArgs.Builder args = new EfArgs.Builder(errs);

      if (declaringType != null) {
        args.add(ctx.getStart(), EfVar.THIS_VAR_NAME, declaringType);
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
      try {
        methodsRegistry.registerMethod(MethodId.of(declaringType, name), method);
      } catch (DuplicateMethodNameException e) {
        errs.add(ctx.methodName().getStart(), e.getMessage());
      }
    }

    @Override
    public void visitErrorNode(@NotNull ErrorNode node) {
      errs.add(node.getSymbol(), "parse error");
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

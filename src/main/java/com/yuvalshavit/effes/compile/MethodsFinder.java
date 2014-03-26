package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.parser.EffesBaseListener;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;
import com.yuvalshavit.util.Dispatcher;
import com.yuvalshavit.util.Util;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
    ParserUtils.walk(new Listener(), source);
  }

  private class Listener extends EffesBaseListener {
    @Override
    public void enterMethodDeclr(@NotNull EffesParser.MethodDeclrContext ctx) {
      String name = ctx.methodName().getText();
//      List<EfType> argTypes = new ArrayList<>(ctx.methodArgs().methodArg().size());
      EfArgs.Builder args = new EfArgs.Builder(errs);

      for (EffesParser.MethodArgContext argContext : ctx.methodArgs().methodArg()) {
        EffesParser.TypeContext typeContext = argContext.type();
        EfType type = typeResolver.getEfType(typeContext);
        Token token = Util.elvis(argContext.type(), EffesParser.TypeContext::getStart);
        String argName = Util.elvis(argContext.VAR_NAME(), TerminalNode::getText);
        args.add(token, argName, type);
      }
      EfType resultType = typeResolver.getEfType(ctx.methodReturnDeclr().type());
      EffesParser.InlinableBlockContext body = ctx.inlinableBlock();
      EfMethod<EffesParser.InlinableBlockContext> method = new EfMethod<>(args.build(), resultType, body);
      try {
        methodsRegistry.registerTopLevelMethod(name, method);
      } catch (DuplicateMethodNameException e) {
        errs.add(ctx.methodName().getStart(), e.getMessage());
      }
    }

    @Override
    public void visitErrorNode(@NotNull ErrorNode node) {
      errs.add(node.getSymbol(), "parse error");
    }
  }

  public static class TypeResolver {
    private final TypeRegistry typeRegistry;
    private final CompileErrors errs;

    public TypeResolver(TypeRegistry typeRegistry, CompileErrors errs) {
      this.typeRegistry = typeRegistry;
      this.errs = errs;
    }

    public EfType getEfType(EffesParser.TypeContext typeContext) {
      return typeContext != null
        ? typesDispatcher.apply(this, typeContext)
        : EfType.UNKNOWN;
    }

    private static final Dispatcher<TypeResolver, EffesParser.TypeContext, EfType> typesDispatcher
      = Dispatcher.builder(TypeResolver.class, EffesParser.TypeContext.class, EfType.class)
      .put(EffesParser.SimpleTypeContext.class, TypeResolver::lookupSimpleType)
      .put(EffesParser.DisunctiveTypeContext.class, TypeResolver::createDisjunctiveType)
      .put(EffesParser.ParenSingleTypeContext.class, TypeResolver::parenType)
      .build();

    private EfType createDisjunctiveType(EffesParser.DisunctiveTypeContext ctx) {
      List<EfType> options = ctx.type().stream().map(this::getEfType).collect(Collectors.toList());
      return new EfType.DisjunctiveType(options);
    }

    private EfType lookupSimpleType(EffesParser.SimpleTypeContext typeContext) {
      return lookupSimpleType(typeContext.TYPE_NAME());
    }

    private EfType lookupSimpleType(TerminalNode typeName) {
      EfType type = typeRegistry.getSimpleType(typeName.getText());
      if (type == null) {
        errs.add(typeName.getSymbol(), "unknown type: " + typeName.getText());
        type = EfType.UNKNOWN;
      }
      return type;
    }

    private EfType parenType(EffesParser.ParenSingleTypeContext ctx) {
      return lookupSimpleType(ctx.TYPE_NAME());
    }
  }
}

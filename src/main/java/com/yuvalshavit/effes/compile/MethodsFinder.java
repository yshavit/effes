package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.parser.EffesBaseListener;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;
import com.yuvalshavit.util.Dispatcher;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class MethodsFinder implements Consumer<EffesParser.CompilationUnitContext> {

  private final TypeRegistry typeRegistry;
  private final MethodsRegistry<EffesParser.InlinableBlockContext> methodsRegistry;
  private final CompileErrors errs;

  public MethodsFinder(TypeRegistry typeRegistry,
                       MethodsRegistry<EffesParser.InlinableBlockContext> methodsRegistry,
                       CompileErrors errs) {
    this.typeRegistry = typeRegistry;
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
      List<EfType> argTypes = new ArrayList<>(ctx.methodArgs().methodArg().size());
      for (EffesParser.MethodArgContext argContext : ctx.methodArgs().methodArg()) {
        EffesParser.TypeContext typeContext = argContext.type();
        EfType type = getEfType(typeContext);
        argTypes.add(type);
      }
      EfType resultType = getEfType(ctx.methodReturnDeclr().type());
      EffesParser.InlinableBlockContext body = ctx.inlinableBlock();
      EfMethod<EffesParser.InlinableBlockContext> method = new EfMethod<>(argTypes, resultType, body);
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

  private EfType getEfType(EffesParser.TypeContext typeContext) {
    return typeContext != null
      ? typesDispatcher.apply(this, typeContext)
      : EfType.UNKNOWN;
  }

  private static final Dispatcher<MethodsFinder, EffesParser.TypeContext, EfType> typesDispatcher
    = Dispatcher.builder(MethodsFinder.class, EffesParser.TypeContext.class, EfType.class)
    .put(EffesParser.SimpleTypeContext.class, MethodsFinder::lookupSimpleType)
    .put(EffesParser.DisunctiveTypeContext.class, MethodsFinder::createDisjunctiveType)
    .put(EffesParser.ParenSingleTypeContext.class, MethodsFinder::parenType)
    .build();

  private EfType createDisjunctiveType(EffesParser.DisunctiveTypeContext ctx) {
    List<EfType> options = ctx.type().stream().map(this::getEfType).collect(Collectors.toList());
    return new EfType.DisjunctiveType(options);
  }

  private EfType.SimpleType lookupSimpleType(EffesParser.SimpleTypeContext typeContext) {
    return lookupSimpleType(typeContext.TYPE_NAME());
  }

  private EfType.SimpleType lookupSimpleType(TerminalNode typeName) {
    EfType.SimpleType type = typeRegistry.getSimpleType(typeName.getText());
    if (type == null) {
      errs.add(typeName.getSymbol(), "unknown type: " + typeName.getText());
    }
    return type;
  }

  private EfType parenType(EffesParser.ParenSingleTypeContext ctx) {
    return lookupSimpleType(ctx.TYPE_NAME());
  }
}

package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.util.Dispatcher;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;
import java.util.stream.Collectors;

public class TypeResolver {
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

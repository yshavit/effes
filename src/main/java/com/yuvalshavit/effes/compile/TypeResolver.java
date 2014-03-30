package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.util.Dispatcher;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TypeResolver implements Function<EffesParser.TypeContext, EfType> {
  private final TypeRegistry typeRegistry;
  private final CompileErrors errs;

  public TypeResolver(TypeRegistry typeRegistry, CompileErrors errs) {
    this.typeRegistry = typeRegistry;
    this.errs = errs;
  }

  @Override
  public EfType apply(EffesParser.TypeContext typeContext) {
    return typeContext != null
      ? typesDispatcher.apply(this, typeContext)
      : EfType.UNKNOWN;
  }

  private static final Dispatcher<TypeResolver, EffesParser.TypeContext, EfType> typesDispatcher
    = Dispatcher.builder(TypeResolver.class, EffesParser.TypeContext.class, EfType.class)
    .put(EffesParser.SimpleTypeContext.class, TypeResolver::lookupSimpleType)
    .put(EffesParser.DisunctiveTypeContext.class, TypeResolver::createDisjunctiveType)
    .build();

  private EfType createDisjunctiveType(EffesParser.DisunctiveTypeContext ctx) {
    List<EfType> options = ctx.type().stream().map(this::apply).collect(Collectors.toList());
    return EfType.disjunction(options);
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
}

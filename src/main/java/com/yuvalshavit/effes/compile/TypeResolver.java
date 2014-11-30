package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.compile.node.CompileErrors;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.effes.parser.EffesParser;
import org.antlr.v4.runtime.tree.TerminalNode;

import javax.annotation.Nullable;

import java.util.Collection;
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
      ? EfType.disjunction(typeContext.singleType().stream().map(this::lookupSingleType).collect(Collectors.toList()))
      : EfType.UNKNOWN;
  }

  @Nullable
  public EfType.SimpleType getSimpleType(String name) {
    return typeRegistry.getSimpleType(name);
  }

  private EfType lookupSingleType(EffesParser.SingleTypeContext ctx) {
    TerminalNode typeName = ctx.TYPE_NAME();
    EfType type = typeRegistry.getType(typeName.getText());
    if (type == null) {
      errs.add(typeName.getSymbol(), "unknown type: " + typeName.getText());
      type = EfType.UNKNOWN;
    } else {
      List<String> genericParams = TypesFinder.buildGenericsParamsList(ctx.genericsDeclr(), errs);
      if (type instanceof EfType.SimpleType) {
        type = type.withRenamedGenericParams(genericParams, errs, ctx.getStart());
      } else if (!genericParams.isEmpty()) {
        throw new UnsupportedOperationException("alias type with generic"); // TODO
      }
    }
    return type;
  }
}

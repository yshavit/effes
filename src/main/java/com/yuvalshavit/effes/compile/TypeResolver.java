package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.compile.node.CompileErrors;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.effes.parser.EffesParser;
import org.antlr.v4.runtime.tree.TerminalNode;

import javax.annotation.Nullable;

import java.util.stream.Collectors;

public class TypeResolver {
  private final TypeRegistry typeRegistry;
  private final CompileErrors errs;
  private final EfType.SimpleType context;
  private final SingleTypeResolver resolver;

  public TypeResolver(TypeRegistry typeRegistry, CompileErrors errs, EfType.SimpleType context) {
    this.typeRegistry = typeRegistry;
    this.errs = errs;
    this.context = context;
    resolver = new SingleTypeResolver();
  }

  public EfType apply(EffesParser.TypeContext typeContext) {
    return typeContext != null
      ? EfType.disjunction(typeContext.singleType().stream().map(resolver).collect(Collectors.toList()))
      : EfType.UNKNOWN;
  }

  @Nullable
  public EfType.SimpleType getSimpleType(String name) { // TODO should this take a reification function?
    return typeRegistry.getSimpleType(name);
  }
  
  class SingleTypeResolver extends SingleTypeHandler<EfType> {
    public SingleTypeResolver() {
      super(errs, EfType.UNKNOWN);
    }

    @Override
    protected EfType lookupDataType(EffesParser.SingleNonGenericContext ctx) {
      TerminalNode typeName = ctx.TYPE_NAME();
      EfType type = typeRegistry.getType(typeName.getText());
      if (type == null) {
        errs.add(typeName.getSymbol(), "unknown type: " + typeName.getText());
        type = EfType.UNKNOWN;
      } else {
        EffesParser.SingleTypeParametersContext params = ctx.singleTypeParameters();
        boolean genericArgsPresent = params.OPEN_BRACKET() != null;
        if (type instanceof EfType.SimpleType) {
          if (genericArgsPresent) throw new UnsupportedOperationException("TODO"); // TODO nested generics? alias types?
          type = type.reify(g -> { throw new UnsupportedOperationException("todo"); }); // TODO
        } else if (genericArgsPresent) {
          throw new UnsupportedOperationException("alias type with generic"); // TODO
        }
      }
      return type;
    }

    @Override
    protected EfType lookupGenericType(EffesParser.SingleGenericTypeContext ctx) {
      String genericName = ctx.GENERIC_NAME().getText();
      return new EfType.GenericType(genericName, context);
    }
  }
}

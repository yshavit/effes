package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.compile.node.CompileErrors;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.effes.parser.EffesParser;
import org.antlr.v4.runtime.tree.TerminalNode;

import javax.annotation.Nullable;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TypeResolver implements Function<EffesParser.TypeContext, EfType> {
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

  @Override
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
          EfType.SimpleType simpleType = (EfType.SimpleType) type;
          if (!genericArgsPresent) {
            throw new UnsupportedOperationException("TODO: generic inference"); // TODO
          } else {
            // TODO there's similar code somewhere -- method lookup?
            List<EfType> paramTypes = params.type().stream().map(TypeResolver.this).collect(Collectors.toList());
            List<EfType.GenericType> declaredGenerics = simpleType.getGenericsDeclr();
            int nExpected = declaredGenerics.size();
            if (nExpected != paramTypes.size()) {
              String msg = String.format("expected %d parameter%s but found %d", nExpected, nExpected == 1 ? "" : "s", paramTypes.size());
              errs.add(params.getStart(), msg);
            }
          }
          type = type.reify(g -> { throw new UnsupportedOperationException("todo"); }); // TODO
        } else if (genericArgsPresent) {
          throw new UnsupportedOperationException("alias type with generic?"); // TODO
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

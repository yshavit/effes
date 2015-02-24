package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.compile.node.CompileErrors;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.effes.parser.EffesParser;

import org.antlr.v4.runtime.Token;
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
  public EfType.SimpleType getSimpleType(String name, Function<EfType.GenericType, EfType> reification) {
    EfType.SimpleType lookup = typeRegistry.getSimpleType(name);
    return lookup == null
      ? null
      : lookup.reify(reification);
  }
  
  public Function<EfType.GenericType,EfType> getReification(Token start, List<EfType.GenericType> typeGenerics, List<EffesParser.TypeContext> declaredGenerics) {
    List<EfType> reificationParams = declaredGenerics.stream().map(this).collect(Collectors.toList());
    if (reificationParams.size() != typeGenerics.size()) {
      String msg = String.format("expected %d type parameter%s but saw %d", typeGenerics.size(), typeGenerics.size() == 1 ? "" : "s", reificationParams.size());
      errs.add(start, msg);
    }
    return g -> {
      int idx = typeGenerics.indexOf(g);
      if (idx < 0) {
        throw new IllegalArgumentException("TODO need a better error message"); // TODO can this even happen?
      }
      if (idx >= reificationParams.size()) {
        errs.add(start, "couldn't find binding for " + g);
        return EfType.UNKNOWN;
      }
      return reificationParams.get(idx);
    };
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
            if (simpleType.getParams().isEmpty()) {
              type = type.reify(t -> t);
            } else {
              List<String> expectedGenericNames = simpleType.getGenericsDeclr().stream().map(EfType.GenericType::getName).collect(Collectors.toList());
              errs.add(ctx.getStop(), "missing generic parameters for " + simpleType.getName() + expectedGenericNames);
              type = EfType.UNKNOWN;
            }
          } else {
            type = type.reify(getReification(params.getStart(), simpleType.getGenericsDeclr(), params.type()));
          }
        } else if (genericArgsPresent) {
          throw new UnsupportedOperationException("alias type with generic?"); // TODO
        }
      }
      return type;
    }

    @Override
    protected EfType lookupGenericType(EffesParser.SingleGenericTypeContext ctx) {
      String genericName = ctx.GENERIC_NAME().getText();
      if (context == null) {
        // global method
        // TODO generics on methods!
        errs.add(ctx.getStart(), "undeclared generic type " + genericName);
        return EfType.UNKNOWN;
      }
      return new EfType.GenericType(genericName, context);
    }
  }
}

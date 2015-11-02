package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.compile.node.CompileErrors;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.effes.parser.EffesParser;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

import javax.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TypeResolver implements Function<EffesParser.TypeContext, EfType> {
  private final TypeRegistry typeRegistry;
  private final CompileErrors errs;
  private final EfType.SimpleType context;
  private final SingleTypeResolver resolver;
  private final Collection<EfType.GenericType> extraGenerics;

  public TypeResolver(TypeRegistry typeRegistry, CompileErrors errs, EfType.SimpleType context) {
    this(typeRegistry, errs, context, Collections.emptySet());
  }

  public TypeResolver(TypeRegistry typeRegistry, CompileErrors errs, EfType.SimpleType context, Collection<EfType.GenericType> extraGenerics) {
    this.typeRegistry = typeRegistry;
    this.errs = errs;
    this.context = context;
    this.extraGenerics = extraGenerics;
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
  
  @Nullable
  public EfType.SimpleType getSimpleType(TerminalNode typeName, List<EffesParser.TypeContext> params) {
    EfType.SimpleType type = typeRegistry.getSimpleType(typeName.getText());
    if (type == null) {
      return null;
    }
    return type.reify(getReification(typeName.getSymbol(), type.getGenericsDeclr(), params));
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
    protected EfType lookupDataType(EffesParser.SingleDataTypeContext ctx) {
      TerminalNode typeName = ctx.TYPE_NAME();
      EfType type = typeRegistry.getType(typeName.getText());
      if (type == null) {
        errs.add(typeName.getSymbol(), "unknown type: " + typeName.getText());
        type = EfType.UNKNOWN;
      } else {
        EffesParser.SingleTypeParametersContext params = ctx.singleTypeParameters();
        Token token = ctx.getStop();
        if (type instanceof EfType.SimpleType) {
          type = reifyOnLookup(((EfType.SimpleType) type), params, context, token);
        } else if (params.OPEN_BRACKET() != null) { // a generic alias (has to be an alias because it's not simple, has to have generics cause it's got a '[')
          throw new UnsupportedOperationException(); // TODO reification of alias, like "ListTail[T] = Empty | List[T]". Not sure how to handle it.
        }
      }
      return type;
    }

    @Override
    protected EfType lookupGenericType(EffesParser.SingleGenericTypeContext ctx) {
      String genericName = ctx.GENERIC_NAME().getText();
      if (context == null) {
        // top-level function
        EfType found = lookUpGenericByName(extraGenerics, genericName);
        if (found == null) {
          errs.add(ctx.getStart(), "undeclared generic type " + genericName);
          return EfType.UNKNOWN;
        } else {
          return found;
        }
      }
      EfType.GenericType genericOnType = lookUpGenericByName(context.getGenericsDeclr(), genericName);
      EfType.GenericType extraGeneric = lookUpGenericByName(extraGenerics, genericName);
      if (genericOnType != null && extraGeneric != null) {
        errs.add(ctx.GENERIC_NAME().getSymbol(), "ambiguous generic " + genericName);
      }
     if (genericOnType != null) {
        return genericOnType;
      } else if (extraGeneric != null) {
        return extraGeneric;
      } else {
        errs.add(ctx.getStart(), String.format("no generic parameter %s defined on %s", genericName, context.getGeneric()));
        return EfType.UNKNOWN;
      }
    }

    private EfType.GenericType lookUpGenericByName(Collection<EfType.GenericType> generics, String genericName) {
      for (EfType.GenericType generic : generics) {
        if (generic.getName().equals(genericName)) {
          return generic;
        }
      }
      return null;
    }
  }

  protected EfType reifyOnLookup(EfType.SimpleType type, EffesParser.SingleTypeParametersContext params, EfType.SimpleType context, Token token) {
    boolean genericArgsPresent = params.OPEN_BRACKET() != null;
    final EfType result;
    if (genericArgsPresent) {
      result = type.reify(getReification(params.getStart(), type.getGenericsDeclr(), params.type()));
    } else {
      if (type.getParams().isEmpty()) {
        result = type;
      } else {
        List<String> expectedGenericNames = type.getGenericsDeclr().stream().map(EfType.GenericType::getName).collect(Collectors.toList());
        errs.add(token, "missing generic parameters for " + type.getName() + expectedGenericNames);
        result = EfType.UNKNOWN;
      }
    }
    return result;
  }
}

package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.parser.EffesBaseListener;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;
import org.antlr.v4.runtime.misc.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class MethodsFinder implements Consumer<EffesParser> {

  private final TypeRegistry typeRegistry;
  private final MethodsRegistry methodsRegistry;

  public MethodsFinder(TypeRegistry typeRegistry, MethodsRegistry methodsRegistry) {
    this.typeRegistry = typeRegistry;
    this.methodsRegistry = methodsRegistry;
  }

  @Override
  public void accept(EffesParser effesParser) {
    ParserUtils.walk(new Listener(), effesParser);
  }

  private class Listener extends EffesBaseListener {
    @Override
    public void enterMethodDeclr(@NotNull EffesParser.MethodDeclrContext ctx) {
      String name = ctx.methodName().getText();
      List<SimpleType> argTypes = new ArrayList<>(ctx.methodArgs().methodArg().size());
      for (EffesParser.MethodArgContext argContext : ctx.methodArgs().methodArg()) {
        EffesParser.TypeContext typeContext = argContext.type();
        SimpleType type = lookupType(typeContext);
        argTypes.add(type);
      }
      SimpleType resultType = lookupType(ctx.methodReturnDeclr().type());
      EfMethod method = new EfMethod(argTypes, resultType);
      methodsRegistry.registerTopLevelMethod(name, method);
    }
  }

  private SimpleType lookupType(EffesParser.TypeContext typeContext) {
    String simpleTypeName = typeContext.TYPE_NAME().getText();
    SimpleType type = typeRegistry.getSimpleType(simpleTypeName);
    if (type == null) {
      throw new MethodRegistrationException("unknown type: " + simpleTypeName);
    }
    return type;
  }
}

package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.parser.EffesBaseListener;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.tree.ErrorNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class MethodsFinder implements Consumer<EffesParser.CompilationUnitContext> {

  private final TypeRegistry typeRegistry;
  private final MethodsRegistry<EffesParser.InlinableBlockContext> methodsRegistry;

  public MethodsFinder(TypeRegistry typeRegistry, MethodsRegistry<EffesParser.InlinableBlockContext> methodsRegistry) {
    this.typeRegistry = typeRegistry;
    this.methodsRegistry = methodsRegistry;
  }

  @Override
  public void accept(EffesParser.CompilationUnitContext source) {
    ParserUtils.walk(new Listener(), source);
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
      EffesParser.InlinableBlockContext body = ctx.inlinableBlock();
      EfMethod<EffesParser.InlinableBlockContext> method = new EfMethod<>(argTypes, resultType, body);
      methodsRegistry.registerTopLevelMethod(name, method);
    }

    @Override
    public void visitErrorNode(@NotNull ErrorNode node) {
      Token t = node.getSymbol();
      String what = node.getText().trim();
      if (!what.isEmpty()) {
        what = ": " + what;
      }
      throw new ParseException(String.format("at line %d, column %d%s", t.getLine(), t.getCharPositionInLine(), what));
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

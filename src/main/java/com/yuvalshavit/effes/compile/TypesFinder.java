package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.parser.EffesBaseListener;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class TypesFinder implements Consumer<EffesParser.CompilationUnitContext> {

  private final TypeRegistry registry;
  private final CompileErrors errs;

  public TypesFinder(TypeRegistry registry, CompileErrors errs) {
    this.registry = registry;
    this.errs = errs;
  }

  @Override
  public void accept(EffesParser.CompilationUnitContext source) {
    ParserUtils.walk(new FindSimpleTypeNames(), source);
    ParserUtils.walk(new FindSimpleTypeArgs(), source);
  }

  private class FindSimpleTypeNames extends EffesBaseListener {
    @Override
    public void enterDataTypeDeclr(@NotNull EffesParser.DataTypeDeclrContext ctx) {
      TerminalNode typeName = ctx.TYPE_NAME();
      registry.registerType(typeName.getSymbol(), typeName.getText());
    }
  }

  private class FindSimpleTypeArgs extends EffesBaseListener {
    private final TypeResolver resolver = new TypeResolver(registry, errs);
    private final List<EfVar> argsBuilder = new ArrayList<>();

    @Override
    public void enterDataTypeDeclr(@NotNull EffesParser.DataTypeDeclrContext ctx) {
      assert argsBuilder.isEmpty() : argsBuilder;
    }

    @Override
    public void exitDataTypeDeclr(@NotNull EffesParser.DataTypeDeclrContext ctx) {
      EfType.SimpleType type = registry.getSimpleType(ctx.TYPE_NAME().getText());
      assert type != null : ctx.TYPE_NAME().getText();
      type.setArgs(argsBuilder);
      argsBuilder.clear();
    }

    @Override
    public void enterDataTypeArgDeclr(@NotNull EffesParser.DataTypeArgDeclrContext ctx) {
      String argName = ctx.VAR_NAME().getText();
      EfType argType = resolver.apply(ctx.type());
      EfVar arg = EfVar.arg(argName, argsBuilder.size(), argType);
      argsBuilder.add(arg);
    }
  }
}

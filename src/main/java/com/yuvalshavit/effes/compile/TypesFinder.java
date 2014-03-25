package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.parser.EffesBaseListener;
import com.yuvalshavit.effes.parser.EffesParser;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.util.function.Consumer;

public final class TypesFinder implements Consumer<EffesParser> {

  private final TypeRegistry registry;

  public TypesFinder(TypeRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void accept(EffesParser effesParser) {
    new ParseTreeWalker().walk(new Listener(), effesParser.compilationUnit());
  }

  private class Listener extends EffesBaseListener {
    @Override
    public void enterDataTypeDeclr(@NotNull EffesParser.DataTypeDeclrContext ctx) {
      registry.registerType(ctx.TYPE_NAME().getText());
    }
  }
}

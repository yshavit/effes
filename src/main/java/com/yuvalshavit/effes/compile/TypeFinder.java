package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.base.BuiltIns;
import com.yuvalshavit.effes.base.Type;
import com.yuvalshavit.effes.base.TypeRegistry;
import com.yuvalshavit.effes.parser.EffesBaseListener;
import com.yuvalshavit.effes.parser.EffesParser;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TypeFinder {
  private final TypeRegistry reg = BuiltIns.createRegistry();
  private final TypeCompiler typeCompiler;

  public TypeFinder() {
    this.typeCompiler = null;
    throw new UnsupportedOperationException("typeCompiler"); // TODO
  }

  void accept(EffesParser.CompilationUnitContext ctx) {
    walk(ctx, new InitialTypeScanner());
    walk(ctx, new TypeDetailScanner());
  }

  private void walk(EffesParser.CompilationUnitContext ctx, org.antlr.v4.runtime.tree.ParseTreeListener listener) {
    new ParseTreeWalker().walk(listener, ctx);
  }

  private final class InitialTypeScanner extends EffesBaseListener {
    @Override
    public void enterTypeDeclr(@NotNull EffesParser.TypeDeclrContext ctx) {
      reg.register(ctx.TYPE_NAME().getText());
    }
  }

  private final class TypeDetailScanner extends EffesBaseListener {
    private Type.SimpleType type;

    @Override
    public void enterTypeDeclr(@NotNull EffesParser.TypeDeclrContext ctx) {
      assert type == null : type; // don't supported nested types yet
      type = reg.getTypeId(ctx.TYPE_NAME().getText());
      // don't rely on the listener to get the generics, do it directly.
      // That's in case there's another generic down the tree.
      EffesParser.GenericsDeclrContext genericsDeclr = ctx.genericsDeclr();
      if (genericsDeclr != null) {
        Map<String, Type> genericsMap = new LinkedHashMap<>();
        for (EffesParser.GenericDeclrContext genericO : genericsDeclr.genericDeclr()) {
          if (!(genericO instanceof EffesParser.NamedGenericContext)) {
            throw new CompileException("illegal generics declaration", genericO.getStart());
            // TODO clean up the grammar so that we don't have to do this parsing work here
          }
          EffesParser.NamedGenericContext generic = (EffesParser.NamedGenericContext) genericO;
          String name = generic.GENERIC_NAME().getText();
          if (genericsMap.containsKey(name)) {
            throw new CompileException("generic " + name + " already declared", generic.GENERIC_NAME().getSymbol());
          }
          EffesParser.GenericParamRestrictionContext restrictionCtx = generic.genericParamRestriction();
          Type restriction = restrictionCtx != null
            ? typeCompiler.aooly(restrictionCtx.type())
            : null;
          genericsMap.put(name, restriction);
        }
        type.setGenerics(genericsMap);
      }
    }

    @Override
    public void enterMethodDeclr(@NotNull EffesParser.MethodDeclrContext ctx) {
      super.enterMethodDeclr(ctx);
      throw new UnsupportedOperationException(); // TODO
    }
  }
}

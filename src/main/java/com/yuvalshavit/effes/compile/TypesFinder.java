package com.yuvalshavit.effes.compile;

import com.google.common.annotations.VisibleForTesting;
import com.yuvalshavit.effes.parser.EffesBaseListener;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.misc.Pair;
import org.antlr.v4.runtime.tree.TerminalNode;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class TypesFinder implements Consumer<EffesParser.CompilationUnitContext> {

  private final TypeRegistry registry;
  private final CompileErrors errs;

  public TypesFinder(TypeRegistry registry, CompileErrors errs) {
    this.registry = registry;
    this.errs = errs;
  }

  @Override
  public void accept(EffesParser.CompilationUnitContext source) {
    ParserUtils.walk(new FindTypeNames(), source);
    ParserUtils.walk(new FindSimpleTypeArgs(), source);
  }

  @VisibleForTesting
  <K extends Comparable<? super K>, V> Map<K, V> createMap() {
    return new HashMap<>();
  }

  private class FindTypeNames extends EffesBaseListener {

    /**
     * Keys are the name of an alias, and the values are a pair whose "a" is the token that declared that alias,
     * and whose "b" is the tokens that it was declared to point to. For instance, given "Boolean = True | False",
     * the key is <tt>"Boolean"</tt>, and the value has <tt>Pair.a = Boolean</tt>, <tt>Pair.b = [True, False]</tt>.
     * We need the String key so that we can look up the values if they're referred to by a subsequent token. For
     * instance, if we later have a declaration <tt>MaybeBool = Boolean | Void</tt>, then to resolve it we may need
     * to resolve <tt>Boolean</tt>. To do that, we need to find its Pair, which is most efficient to do via a String
     * lookup (since Token doesn't define equals/hashCode based on getText).
     */
    private final Map<String, Pair<Token, Collection<Token>>> aliases = createMap();

    @Override
    public void enterDataTypeDeclr(@NotNull EffesParser.DataTypeDeclrContext ctx) {
      TerminalNode typeName = ctx.TYPE_NAME();
      registry.registerType(typeName.getSymbol(), typeName.getText());
    }

    @Override
    public void enterTypeAliasDeclr(@NotNull EffesParser.TypeAliasDeclrContext ctx) {
      if (ctx.name == null || ctx.targets == null) {
        errs.add(ctx.getStart(), "unrecognized targets for type alias");
      } else {
        String name = ctx.name.getText();
        Set<String> targetNames = new HashSet<>();
        ctx.targets.stream().forEach(target -> {
          if (!targetNames.add(target.getText())) {
            errs.add(target, String.format("duplicate target '%s' for type alias '%s'", target.getText(), name));
          }
        });
        aliases.put(name, new Pair<>(ctx.name, ctx.targets));
      }
    }

    @Override
    public void exitCompilationUnit(@NotNull EffesParser.CompilationUnitContext ctx) {
      aliases.forEach((nameToken, aliasInfo) -> buildAlias(aliasInfo.a, aliasInfo.b, new HashSet<>()));
    }

    @Nullable
    private EfType buildAlias(Token aliasNameToken, Collection<Token> targetTokens, Set<String> building) {
      String aliasName = aliasNameToken.getText();
      if (registry.getType(aliasName) != null) {
        assert aliases.containsKey(aliasName) : aliasName;
        return registry.getType(aliasName);
      }
      if (!building.add(aliasName)) {
        errs.add(aliasNameToken, "cyclic type alias definition");
        return null;
      }

      Collection<EfType> targets = new ArrayList<>(targetTokens.size());
      targetTokens.forEach(targetTok -> {
        String targetName = targetTok.getText();
        EfType target = registry.getType(targetName);
        if (target == null) {
          // Not already known, so targetName may refer to an alias that hasn't been built yet; try to build it.
          Pair<Token, Collection<Token>> targetInfo = aliases.get(targetName);
          if (targetInfo != null) {
            target = buildAlias(targetInfo.a, targetInfo.b, building);
          }
        }
        if (target == null) {
          if (!aliases.containsKey(targetName)) {
            errs.add(targetTok, "unknown type");
          }
        } else {
          targets.add(target);
        }
      });

      building.remove(aliasName);

      EfType aliasType = EfType.disjunction(targets);
      registry.registerAlias(aliasNameToken, aliasName, aliasType);
      return aliasType;
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

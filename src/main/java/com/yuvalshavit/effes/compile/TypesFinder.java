package com.yuvalshavit.effes.compile;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
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
import java.util.stream.Collectors;

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
    ParserUtils.walk(new FindOpenTypes(), source).finish();
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
        List<Token> targetTokens = new ArrayList<>();
        ctx.targets.singleType().stream().forEach(targetCtx -> {
          Token tok = targetCtx.TYPE_NAME().getSymbol();
          if (!targetNames.add(tok.getText())) {
            errs.add(tok, String.format("duplicate target '%s' for type alias '%s'", tok.getText(), name));
          } else {
            targetTokens.add(tok);
          }
        });
        aliases.put(name, new Pair<>(ctx.name, targetTokens));
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

  private class FindOpenTypes extends EffesBaseListener {

    private final Map<String, Token> openTypeDeclrs = new HashMap<>();
    private final Multimap<String, String> typeMappings = HashMultimap.create();
    private String currentDataType;

    @Override
    public void enterOpenTypeDeclr(@NotNull EffesParser.OpenTypeDeclrContext ctx) {
      Token nameToken = ctx.name;
      if (registry.getType(nameToken.getText()) != null || openTypeDeclrs.containsKey(nameToken.getText())) {
        errs.add(nameToken, "duplicate type name");
      } else {
        Token old = openTypeDeclrs.put(nameToken.getText(), nameToken);
        assert old == null : nameToken.getText() + " -> " + nameToken;
      }
    }

    @Override
    public void enterDataTypeDeclr(@NotNull EffesParser.DataTypeDeclrContext ctx) {
      assert currentDataType == null : currentDataType;
      currentDataType = ctx.TYPE_NAME().getText();
    }

    @Override
    public void exitDataTypeDeclr(@NotNull EffesParser.DataTypeDeclrContext ctx) {
      assert currentDataType != null;
      currentDataType = null;
    }

    @Override
    public void enterOpenTypeAlternative(@NotNull EffesParser.OpenTypeAlternativeContext ctx) {
      assert currentDataType != null;
      TerminalNode openTypeName = ctx.singleType().TYPE_NAME();
      if (openTypeName != null) {
        typeMappings.put(openTypeName.getText(), currentDataType);
      } else {
        errs.add(ctx.getStart(), "expected name of open type");
      }
    }

    public void finish() {
      for (Map.Entry<String, Collection<String>> openTypeToAlts : typeMappings.asMap().entrySet()) {
        String openTypeName = openTypeToAlts.getKey();
        Collection<String> alternativeNames = openTypeToAlts.getValue();
        EfType alternatives = EfType.disjunction(
          alternativeNames.stream().map(registry::getSimpleType).collect(Collectors.toList()));
        registry.registerAlias(openTypeDeclrs.get(openTypeName), openTypeName, alternatives);
      }
      openTypeDeclrs.entrySet().forEach(entry -> {
        if (!typeMappings.containsKey(entry.getKey())) {
          // The entry is for an open type that's not used by any concrete type. That is, it has no alternatives. It
          // doesn't matter what type we assign it, but we do want to give it some type, for MethodsFinder's sake.
          registry.registerAlias(entry.getValue(), entry.getKey(), EfType.UNKNOWN);
        }
      });
    }
  }
}

package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.parser.EffesParser;
import org.antlr.v4.runtime.Token;

import java.util.List;

public final class IrCompiler { // TODO un-generic, and get rid of the builtins? Maybe?

  private final MethodsRegistry<Block> compiledMethods;
  private final CompileErrors errs;

  public IrCompiler(EffesParser.CompilationUnitContext source,
                    TypeRegistry baseTypeRegistry,
                    MethodsRegistry<?> builtinsRegistry,
                    CompileErrors errs)
  {
    this.errs = errs;
    TypeRegistry typeRegistry = getTypeRegistry(source, errs, baseTypeRegistry);
    compiledMethods = compileToIntermediate(source, typeRegistry, builtinsRegistry, errs);
  }

  private static TypeRegistry getTypeRegistry(EffesParser.CompilationUnitContext source,
                                              CompileErrors errs,
                                              TypeRegistry typeRegistry) {
    new TypesFinder(typeRegistry).accept(source);
    return typeRegistry;
  }

  public CompileErrors getErrors() {
    return errs;
  }

  public MethodsRegistry<Block> getCompiledMethods() {
    checkNoErrors();
    return compiledMethods;
  }

  private void checkNoErrors() {
    if (errs.hasErrors()) {
      throw new IllegalArgumentException("compilation errors");
    }
  }


  private static MethodsRegistry<Block> compileToIntermediate(EffesParser.CompilationUnitContext source,
                                                              TypeRegistry typeRegistry,
                                                              MethodsRegistry<?> builtinsRegistry,
                                                              CompileErrors errs)
  {
    MethodsRegistry<EffesParser.InlinableBlockContext> unparsedMethods = new MethodsRegistry<>();
    new MethodsFinder(typeRegistry, unparsedMethods, errs).accept(source);

    Scopes<EfVar, Token> vars = new Scopes<>(
      EfVar::getName,
      (name, token) -> errs.add(token, String.format("duplicate variable name '%s'", name)));
    ExpressionCompiler expressionCompiler = new ExpressionCompiler(unparsedMethods, builtinsRegistry, typeRegistry, errs, vars);
    StatementCompiler statementCompiler = new StatementCompiler(expressionCompiler, vars);
    BlockCompiler blockCompiler = new BlockCompiler(statementCompiler);

    MethodsRegistry<Block> compiled = unparsedMethods.transform(parsedMethod -> {
      try (Scopes.ScopeCloser ignored = vars.pushScope()){
        List<EfArgs.Arg> asList = parsedMethod.getArgs().asList();
        for (int pos = 0, len = asList.size(); pos < len; ++pos) {
          EfArgs.Arg arg = asList.get(pos);
          EfVar argVar = EfVar.arg(arg.name(), pos, arg.type());
          vars.add(argVar, null);
        }
        return blockCompiler.apply(parsedMethod.getBody());
      }
    });
    for (EfMethod<? extends Block> method : compiled.getTopLevelMethods()) {
      method.getBody().validate(method.getResultType(), errs);
    }
    return compiled;
  }
}

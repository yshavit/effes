package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.parser.EffesParser;
import org.antlr.v4.runtime.Token;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public final class IrCompiler<E> {

  private final MethodsRegistry<E> builtinsRegistry;
  private final MethodsRegistry<Block> compiledMethods;
  private final CompileErrors errs;

  public IrCompiler(EffesParser.CompilationUnitContext source,
                    Function<TypeRegistry, BuiltInMethodsFactory<E>> builtInMethods)
  {
    errs = new CompileErrors();
    TypeRegistry typeRegistry = getTypeRegistry(source, errs);
    builtinsRegistry = getBuiltins(typeRegistry, builtInMethods);
    compiledMethods = compileToIntermediate(source, typeRegistry, builtinsRegistry, errs);
  }

  private static TypeRegistry getTypeRegistry(EffesParser.CompilationUnitContext source, CompileErrors errs) {
    TypeRegistry typeRegistry = new TypeRegistry(errs);
    new TypesFinder(typeRegistry).accept(source);
    return typeRegistry;
  }

  public CompileErrors getErrors() {
    return errs;
  }

  public MethodsRegistry<E> getBuiltinsRegistry() {
    checkNoErrors();
    return builtinsRegistry;
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

  private static <E> MethodsRegistry<E> getBuiltins(TypeRegistry typeRegistry,
                                                    Function<TypeRegistry, BuiltInMethodsFactory<E>> builtInMethods)
  {
    BuiltInMethodsFactory<E> builtIns = builtInMethods.apply(typeRegistry);
    MethodsRegistry<E> builtinsRegistry = new MethodsRegistry<>();
    builtIns.addTo(typeRegistry, builtinsRegistry);
    return builtinsRegistry;
  }


  private static <E> MethodsRegistry<Block> compileToIntermediate(EffesParser.CompilationUnitContext source,
                                                                  TypeRegistry typeRegistry,
                                                                  MethodsRegistry<E> builtinsRegistry,
                                                                  CompileErrors errs)
  {
    MethodsRegistry<EffesParser.InlinableBlockContext> unparsedMethods = new MethodsRegistry<>();
    new MethodsFinder(typeRegistry, unparsedMethods, errs).accept(source);

    Scopes<EfVar, Token> vars = new Scopes<>(
      EfVar::getName,
      (name, token) -> errs.add(token, String.format("duplicate variable name '%s'", name)));
    ExpressionCompiler expressionCompiler = new ExpressionCompiler(unparsedMethods, typeRegistry, errs, vars);
    StatementCompiler statementCompiler = new StatementCompiler(
      expressionCompiler,
      unparsedMethods,
      builtinsRegistry,
      errs);
    BlockCompiler blockCompiler = new BlockCompiler(statementCompiler);

    MethodsRegistry<Block> compiled = unparsedMethods.transform(parsedMethod -> {
      vars.pushScope();
      List<EfArgs.Arg> asList = parsedMethod.getArgs().asList();
      for (int pos = 0, len = asList.size(); pos < len; ++pos) {
        EfArgs.Arg arg = asList.get(pos);
        EfVar argVar = EfVar.arg(arg.name(), pos, arg.type());
        vars.add(argVar, null);
      }
      Block r = blockCompiler.apply(parsedMethod.getBody());
      vars.popScope();
      return r;
    });
    for (EfMethod<? extends Block> method : compiled.getTopLevelMethods()) {
      method.getBody().validate(method.getResultType(), errs);
    }
    return compiled;
  }
}

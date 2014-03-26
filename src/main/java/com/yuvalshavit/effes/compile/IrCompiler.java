package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.parser.EffesParser;

import java.util.function.Function;

public final class IrCompiler<E> {

  private final MethodsRegistry<E> builtinsRegistry;
  private final MethodsRegistry<Block> compiledMethods;

  public IrCompiler(EffesParser.CompilationUnitContext source,
                    Function<TypeRegistry, BuiltInMethodsFactory<E>> builtInMethods)
  {
    TypeRegistry typeRegistry = getTypeRegistry(source);
    builtinsRegistry = getBuiltins(typeRegistry, builtInMethods);
    compiledMethods = compilerIr(source, typeRegistry, builtinsRegistry);
  }

  private static TypeRegistry getTypeRegistry(EffesParser.CompilationUnitContext source) {
    TypeRegistry typeRegistry = new TypeRegistry();
    new TypesFinder(typeRegistry).accept(source);
    return typeRegistry;
  }

  public MethodsRegistry<E> getBuiltinsRegistry() {
    return builtinsRegistry;
  }

  public MethodsRegistry<Block> getCompiledMethods() {
    return compiledMethods;
  }

  private static <E> MethodsRegistry<E> getBuiltins(TypeRegistry typeRegistry,
                                                    Function<TypeRegistry, BuiltInMethodsFactory<E>> builtInMethods)
  {
    BuiltInMethodsFactory<E> builtIns = builtInMethods.apply(typeRegistry);
    MethodsRegistry<E> builtinsRegistry = new MethodsRegistry<>();
    builtIns.addTo(typeRegistry, builtinsRegistry);
    return builtinsRegistry;
  }


  private static <E> MethodsRegistry<Block> compilerIr(EffesParser.CompilationUnitContext source,
                                                   TypeRegistry typeRegistry,
                                                   MethodsRegistry<E> builtinsRegistry) {
    MethodsRegistry<EffesParser.InlinableBlockContext> unparsedMethods = new MethodsRegistry<>();
    new MethodsFinder(typeRegistry, unparsedMethods).accept(source);

    StatementCompiler statementCompiler = new StatementCompiler(
      new ExpressionCompiler(unparsedMethods, typeRegistry),
      unparsedMethods,
      builtinsRegistry);
    BlockCompiler blockCompiler = new BlockCompiler(statementCompiler);
    return unparsedMethods.compileMethods(blockCompiler);
  }
}

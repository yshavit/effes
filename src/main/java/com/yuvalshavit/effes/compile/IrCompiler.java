package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.compile.node.Block;
import com.yuvalshavit.effes.compile.node.CompileErrors;
import com.yuvalshavit.effes.compile.node.EfArgs;
import com.yuvalshavit.effes.compile.node.EfMethod;
import com.yuvalshavit.effes.compile.node.EfVar;
import com.yuvalshavit.effes.parser.EffesParser;
import org.antlr.v4.runtime.Token;

import java.util.List;
import java.util.function.BiFunction;

public final class IrCompiler<E> {

  private final MethodsRegistry<Block> compiledMethods;
  private final CompileErrors errs;
  private final MethodsRegistry<E> builtInMethods;

  public IrCompiler(EffesParser.CompilationUnitContext source,
                    BiFunction<TypeRegistry, CompileErrors, MethodsRegistry<E>> builtinsRegistryF,
                    CompileErrors errs)
  {
    this.errs = errs;
    CtorRegistry ctors = new CtorRegistry();
    TypeRegistry typeRegistry = getTypeRegistry(source, new TypeRegistry(errs), ctors, errs);
    builtInMethods = builtinsRegistryF.apply(typeRegistry, errs);
    compiledMethods = compileToIntermediate(source, typeRegistry, ctors, builtInMethods, errs);
  }

  private static TypeRegistry getTypeRegistry(EffesParser.CompilationUnitContext source,
                                              TypeRegistry typeRegistry,
                                              CtorRegistry ctors,
                                              CompileErrors errs) {
    new TypesFinder(typeRegistry, ctors, errs).accept(source);
    return typeRegistry;
  }

  public MethodsRegistry<E> getBuiltInMethods() {
    return builtInMethods;
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
    CtorRegistry ctors,
    MethodsRegistry<?> builtinsRegistry,
    CompileErrors errs)
  {
    MethodsRegistry<EffesParser.InlinableBlockContext> unparsedMethods = new MethodsRegistry<>();
    new MethodsFinder(typeRegistry, unparsedMethods, ctors, errs).accept(source);

    Scopes<EfVar, Token> vars = new Scopes<>(
      EfVar::getName,
      (name, token) -> errs.add(token, String.format("duplicate variable name '%s'", name)));
    ExpressionCompiler expressionCompiler = new ExpressionCompiler(unparsedMethods, builtinsRegistry, typeRegistry, ctors, errs, vars);
    StatementCompiler statementCompiler = new StatementCompiler(expressionCompiler, vars);
    BlockCompiler blockCompiler = new BlockCompiler(statementCompiler);

    MethodsRegistry<Block> compiled = unparsedMethods.transform( (methodId, parsedMethod) -> vars.inScope(() -> {
      expressionCompiler.setDeclaringType(methodId.getDefinedOn());
      List<EfArgs.Arg> argsList = parsedMethod.getArgs().asList();
      vars.setElemsCountOffset(argsList.size());
      for (int pos = 0, len = argsList.size(); pos < len; ++pos) {
        EfArgs.Arg arg = argsList.get(pos);
        EfVar argVar = EfVar.arg(arg.name(), pos, arg.type());
        vars.add(argVar, null);
      }
      return blockCompiler.apply(parsedMethod.getBody(), parsedMethod.getResultType());
    }));
    for (EfMethod<? extends Block> method : compiled.getMethods()) {
      NodeStateListener.accept(method.getBody(), child -> child.validate(errs));
      method.getBody().validateResultType(method.getResultType(), errs);
    }
    return compiled;
  }
}

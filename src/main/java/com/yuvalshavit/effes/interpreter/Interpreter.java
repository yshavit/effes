package com.yuvalshavit.effes.interpreter;

import com.google.common.collect.ImmutableList;
import com.yuvalshavit.effes.compile.Block;
import com.yuvalshavit.effes.compile.BlockCompiler;
import com.yuvalshavit.effes.compile.BuiltInMethodsFactory;
import com.yuvalshavit.effes.compile.EfMethod;
import com.yuvalshavit.effes.compile.ExpressionCompiler;
import com.yuvalshavit.effes.compile.MethodsFinder;
import com.yuvalshavit.effes.compile.MethodsRegistry;
import com.yuvalshavit.effes.compile.StatementCompiler;
import com.yuvalshavit.effes.compile.TypeRegistry;
import com.yuvalshavit.effes.compile.TypesFinder;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;

import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.function.Function;

public final class Interpreter {
  private final MethodsRegistry<ExecutableElement> methodsRegistry;

  public Interpreter(EffesParser.CompilationUnitContext source, PrintStream out) {
    TypeRegistry typeRegistry = new TypeRegistry();
    new TypesFinder(typeRegistry).accept(source);

    BuiltInMethodsFactory<ExecutableElement> builtIns = new ExecutableBuiltInMethods(typeRegistry, out);
    MethodsRegistry<ExecutableElement> builtinsRegistry = new MethodsRegistry<>();
    builtIns.addTo(typeRegistry, builtinsRegistry);

    MethodsRegistry<EffesParser.InlinableBlockContext> unparsedMethods = new MethodsRegistry<>();
    new MethodsFinder(typeRegistry, unparsedMethods).accept(source);

    StatementCompiler statementCompiler = new StatementCompiler(
      new ExpressionCompiler(typeRegistry),
      unparsedMethods,
      builtinsRegistry);
    BlockCompiler blockCompiler = new BlockCompiler(statementCompiler);
    MethodsRegistry<Block> compiledMethods = unparsedMethods.compileMethods(blockCompiler);

    ExecutableExpressionCompiler executableExpressionCompiler = new ExecutableExpressionCompiler();
    MethodsRegistry<ExecutableElement> executableMethods = new MethodsRegistry<>();
    Function<String, ExecutableElement> methodLookup = m -> {
      EfMethod<? extends ExecutableElement> method = executableMethods.getMethod(m);
      assert method != null : m;
      return method.getBody();
    };

    Function<String, ExecutableElement> builtInMethodsLookup = name -> {
      EfMethod<? extends ExecutableElement> method = builtinsRegistry.getMethod(name);
      assert method != null;
      return method.getBody();
    };
    ExecutableStatementCompiler executableStatementCompiler = new ExecutableStatementCompiler(
      executableExpressionCompiler,
      methodLookup,
      builtInMethodsLookup);
    ExecutableBlockCompiler executableBlockCompiler = new ExecutableBlockCompiler(executableStatementCompiler);
    executableMethods.addAll(compiledMethods, executableBlockCompiler);

    this.methodsRegistry = executableMethods;
  }

  public Interpreter(EffesParser.CompilationUnitContext source) {
    this(source, System.out);
  }

  public void runMain() {
    EfMethod<? extends ExecutableElement> main = methodsRegistry.getMethod("main");
    StateStack states = new StateStack();
    if (main != null) {
      if(!main.getArgTypes().isEmpty()) {
        throw new BadMainException("main method may not take any arguments");
      }
      ExecutableStatement.MethodInvoke.invoke(main.getBody(), ImmutableList.of(), states);
    }
    assert states.getAllStates().isEmpty() : states.getAllStates();
  }

  public static void main(String[] args) throws IOException {
    try (FileReader tmpFile = new FileReader("/tmp/ramdisk/example.ef")) {
      EffesParser parser = ParserUtils.createParser(tmpFile);
      new Interpreter(parser.compilationUnit()).runMain();
      System.err.println(">> Done!");
    }
  }
}

package com.yuvalshavit.effes.interpreter;

import com.google.common.collect.ImmutableList;
import com.yuvalshavit.effes.compile.Block;
import com.yuvalshavit.effes.compile.BlockCompiler;
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
import java.util.function.Function;

public final class Interpreter {
  private final MethodsRegistry<ExecutableBlock> methodsRegistry;

  public Interpreter(EffesParser.CompilationUnitContext source) {
    TypeRegistry typeRegistry = new TypeRegistry();
    new TypesFinder(typeRegistry).accept(source);

    MethodsRegistry<EffesParser.InlinableBlockContext> unparsedMethods = new MethodsRegistry<>();
    new MethodsFinder(typeRegistry, unparsedMethods).accept(source);

    StatementCompiler statementCompiler = new StatementCompiler(new ExpressionCompiler(typeRegistry), unparsedMethods);
    BlockCompiler blockCompiler = new BlockCompiler(statementCompiler);
    MethodsRegistry<Block> compiledMethods = unparsedMethods.compileMethods(blockCompiler);

    ExecutableExpressionCompiler executableExpressionCompiler = new ExecutableExpressionCompiler();
    MethodsRegistry<ExecutableBlock> executableMethods = new MethodsRegistry<>();
    Function<String, ExecutableBlock> methodLookup = m -> {
      EfMethod<ExecutableBlock> method = executableMethods.getMethod(m);
      assert method != null : m;
      return method.getBody();
    };
    ExecutableStatementCompiler executableStatementCompiler = new ExecutableStatementCompiler(executableExpressionCompiler, methodLookup);
    ExecutableBlockCompiler executableBlockCompiler = new ExecutableBlockCompiler(executableStatementCompiler);
    executableMethods.addAll(compiledMethods, executableBlockCompiler);
    this.methodsRegistry = executableMethods;
  }

  public void runMain() {
    EfMethod<ExecutableBlock> main = methodsRegistry.getMethod("main");
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
      System.out.println(">> Done!");
    }
  }
}

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
  public static StateStack run(EffesParser.CompilationUnitContext source) {
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

    EfMethod<ExecutableBlock> main = executableMethods.getMethod("main");
    StateStack states = new StateStack();
    if (main != null) {
      if(!main.getArgTypes().isEmpty()) {
        throw new BadMainException("main method may not take any arguments");
      }
      ExecutableStatement.MethodInvoke.invoke(main.getBody(), ImmutableList.of(), states);
    }
    return states;
  }

  private static class BadMainException extends RuntimeException {
    private BadMainException(String message) {
      super(message);
    }
  }

  public static void main(String[] args) throws IOException {
    try (FileReader tmpFile = new FileReader("/tmp/ramdisk/example.ef")) {
      EffesParser parser = ParserUtils.createParser(tmpFile);
      StateStack states = run(parser.compilationUnit());
      System.out.println(">> " + states.getAllStates());
    }
  }
}

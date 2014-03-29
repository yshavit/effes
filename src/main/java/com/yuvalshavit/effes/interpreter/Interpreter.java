package com.yuvalshavit.effes.interpreter;

import com.google.common.collect.ImmutableList;
import com.yuvalshavit.effes.compile.Block;
import com.yuvalshavit.effes.compile.CompileErrors;
import com.yuvalshavit.effes.compile.EfMethod;
import com.yuvalshavit.effes.compile.IrCompiler;
import com.yuvalshavit.effes.compile.MethodsRegistry;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.function.Function;

public final class Interpreter {
  private final MethodsRegistry<ExecutableElement> methodsRegistry;
  private final CompileErrors errs;

  public Interpreter(EffesParser.CompilationUnitContext source, PrintStream out) {
    IrCompiler<ExecutableElement> compiler = new IrCompiler<>(source, t -> new ExecutableBuiltInMethods(t, out));
    CompileErrors errs = compiler.getErrors();
    if (errs.hasErrors()) {
      this.errs = errs;
      this.methodsRegistry = null;
      return;
    }

    MethodsRegistry<ExecutableElement> builtinsRegistry = compiler.getBuiltinsRegistry();
    MethodsRegistry<Block> compiledMethods = compiler.getCompiledMethods();

    MethodsRegistry<ExecutableElement> executableMethods = new MethodsRegistry<>();
    ExecutableExpressionCompiler executableExpressionCompiler = new ExecutableExpressionCompiler(executableMethods);
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
    executableMethods.addAll(compiledMethods, m -> executableBlockCompiler.apply(m.getBody()));

    this.errs = null;
    this.methodsRegistry = executableMethods;
  }

  public Interpreter(EffesParser.CompilationUnitContext source) {
    this(source, System.out);
  }

  public boolean hasErrors() {
    return errs != null;
  }

  public CompileErrors getErrors() {
    return errs != null
      ? errs
      : new CompileErrors();
  }

  public Object runMain() {
    if (hasErrors()) {
      throw new IllegalStateException("compilation had errors");
    }
    EfMethod<? extends ExecutableElement> main = methodsRegistry.getMethod("main");
    if (main != null) {
      CallStack states = new CallStack();
      Object initial = states.snapshot();
      if(main.getArgs().length() != 0) {
        throw new BadMainException("main method may not take any arguments");
      }
      ExecutableExpression.MethodInvokeExpression.invoke(main.getBody(), ImmutableList.of(), states);
      Object rv = states.pop();
      assert states.snapshot().equals(initial) : states.snapshot();
      return rv;
    } else {
      return null;
    }
  }

  public static void main(String[] args) throws IOException {
    File home = new File(System.getProperty("user.home"));
    File file = new File(home, "Desktop/example.ef");
    try (FileReader tmpFile = new FileReader(file)) {
      EffesParser parser = ParserUtils.createParser(tmpFile);
      Interpreter interpreter = new Interpreter(parser.compilationUnit());
      if (interpreter.hasErrors()) {
        interpreter.getErrors().getErrors().forEach(System.err::println);
        System.err.println(">> Compilation had errors; not executing code.");
      } else {
        System.err.printf(">> %s%n", interpreter.runMain());
      }
    }
  }
}

package com.yuvalshavit.effes.interpreter;

import com.google.common.collect.ImmutableList;
import com.yuvalshavit.effes.compile.Block;
import com.yuvalshavit.effes.compile.CompileErrors;
import com.yuvalshavit.effes.compile.EfMethod;
import com.yuvalshavit.effes.compile.EfType;
import com.yuvalshavit.effes.compile.IrCompiler;
import com.yuvalshavit.effes.compile.MethodId;
import com.yuvalshavit.effes.compile.MethodsRegistry;
import com.yuvalshavit.effes.compile.TypeRegistry;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.function.Function;

public final class Interpreter {
  private final MethodsRegistry<ExecutableMethod> methodsRegistry;
  private final CompileErrors errs;

  public Interpreter(EffesParser.CompilationUnitContext source, PrintStream out) {
    CompileErrors errs = new CompileErrors();

    IrCompiler<ExecutableMethod> compiler = new IrCompiler<>(source, (t, e) -> getBuiltins(out, t, e), errs);
    if (errs.hasErrors()) {
      this.errs = errs;
      this.methodsRegistry = null;
      return;
    }

    MethodsRegistry<Block> compiledMethods = compiler.getCompiledMethods();

    MethodsRegistry<ExecutableMethod> executableMethods = new MethodsRegistry<>();
    Function<MethodId, ExecutableMethod> methodLookup = m -> {
      EfMethod<? extends ExecutableMethod> method = executableMethods.getMethod(m);
      assert method != null : m;
      return method.getBody();
    };
    Function<MethodId, ExecutableMethod> builtInMethodsLookup = name -> {
      EfMethod<? extends ExecutableMethod> method = compiler.getBuiltInMethods().getMethod(name);
      assert method != null;
      return method.getBody();
    };
    ExecutableExpressionCompiler executableExpressionCompiler = new ExecutableExpressionCompiler(
      methodLookup,
      builtInMethodsLookup);
    ExecutableStatementCompiler executableStatementCompiler = new ExecutableStatementCompiler(
      executableExpressionCompiler);
    ExecutableBlockCompiler executableBlockCompiler = new ExecutableBlockCompiler(executableStatementCompiler);
    executableMethods.addAll(compiledMethods, m -> executableBlockCompiler.apply(m.getBody()));

    this.errs = null;
    this.methodsRegistry = executableMethods;
  }

  private static MethodsRegistry<ExecutableMethod> getBuiltins(PrintStream out, TypeRegistry typeRegistry,
                                                               CompileErrors errs) {
    MethodsRegistry<ExecutableMethod> builtInMethods = new MethodsRegistry<>();
    ExecutableBuiltInMethods builtIns = new ExecutableBuiltInMethods(typeRegistry, out);
    builtIns.addTo(typeRegistry, builtInMethods, errs);
    return builtInMethods;
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
    EfMethod<? extends ExecutableMethod> main = methodsRegistry.getMethod(MethodId.topLevel("main"));
    if (main != null) {
      CallStack states = new CallStack();
      Object initial = states.snapshot();
      if(main.getArgs().length() != 0) {
        throw new BadMainException("main method may not take any arguments");
      }
      boolean hasRv = !EfType.VOID.equals(main.getResultType());
      ExecutableExpression.MethodInvokeExpression.invoke(main.getBody(), ImmutableList.of(), states, hasRv);
      Object rv;
      if (hasRv) {
        rv = states.pop();
      } else {
        rv = null;
      }
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
    } catch (Exception | AssertionError e) {
      e.printStackTrace();
    }
  }
}

package com.yuvalshavit.effes.interpreter;

import com.yuvalshavit.effes.compile.BuiltInMethodsFactory;
import com.yuvalshavit.effes.compile.TypeRegistry;

import java.io.PrintStream;

public final class ExecutableBuiltInMethods implements BuiltInMethodsFactory<ExecutableMethod> {
  private final TypeRegistry typeRegistry;
  private final PrintStream out;

  public ExecutableBuiltInMethods(TypeRegistry typeRegistry, PrintStream out) {
    this.typeRegistry = typeRegistry;
    this.out = out;
  }

  @Override
  public ExecutableMethod print() {
    return method(stack -> {
      out.println("Your argument was: " + stack.peekArg(0));
      stack.push(typeRegistry.getSimpleType("Void"));
      stack.popToRv();
    });
  }

  private static ExecutableMethod method(ExecutableElement body) {
    return new ExecutableMethod() {
      @Override
      public int nVars() {
        return 0; // the body's code is Java and can just use normal Java vars
      }

      @Override
      public void execute(CallStack stack) {
        body.execute(stack);
      }
    };
  }
}

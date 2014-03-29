package com.yuvalshavit.effes.interpreter;

import com.yuvalshavit.effes.compile.BuiltInMethodsFactory;
import com.yuvalshavit.effes.compile.TypeRegistry;

import java.io.PrintStream;

public final class ExecutableBuiltInMethods implements BuiltInMethodsFactory<ExecutableElement> {
  private final TypeRegistry typeRegistry;
  private final PrintStream out;

  public ExecutableBuiltInMethods(TypeRegistry typeRegistry, PrintStream out) {
    this.typeRegistry = typeRegistry;
    this.out = out;
  }

  @Override
  public ExecutableElement print() {
    return stack -> {
      out.println("Your argument was: " + stack.peekArg(0));
      stack.push(typeRegistry.getSimpleType("Void"));
      stack.popToRv();
    };
  }
}

package com.yuvalshavit.effes.interpreter;

import com.yuvalshavit.effes.compile.node.BuiltInMethodsFactory;
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
      out.println("Your argument was: " + stack.peekArg(0).getUserVisibleString());
      stack.push(typeRegistry.getSimpleType("Void"), 0); // TODO need some mechanism to ensure that Void has no args!
      stack.popToRv();
    });
  }

  @Override
  public ExecutableMethod addInt() {
    return method(stack -> {
      EfValue.LongValue lhs = (EfValue.LongValue) stack.peekArg(0);
      EfValue.LongValue rhs = (EfValue.LongValue) stack.peekArg(1);
      stack.push(EfValue.of(lhs.getValue() + rhs.getValue()));
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

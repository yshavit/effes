package com.yuvalshavit.effes.interpreter;

import com.yuvalshavit.effes.compile.node.BuiltInMethodsFactory;
import com.yuvalshavit.effes.compile.TypeRegistry;

import java.io.PrintStream;
import java.util.function.BiFunction;
import java.util.function.LongBinaryOperator;

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
      out.println(stack.peekArg(0).getUserVisibleString());
      stack.push(typeRegistry.getSimpleType("Void"), 0); // TODO need some mechanism to ensure that Void has no args!
      stack.popToRv();
    });
  }

  @Override
  public ExecutableMethod addInt() {
    return binaryLongMethod(Long::sum);
  }

  @Override
  public ExecutableMethod subInt() {
    return binaryLongMethod((l, r) -> l -r);
  }

  @Override
  public ExecutableMethod multInt() {
    return binaryLongMethod((l, r) -> l * r);
  }

  @Override
  public ExecutableMethod divInt() {
    return binaryLongMethod((l, r) -> l / r);
  }
  
  private static ExecutableMethod binaryLongMethod(LongBinaryOperator op) {
    return binaryMethod(EfValue.LongValue.class, (lhs, rhs) -> EfValue.of(op.applyAsLong(lhs.getValue(), rhs.getValue())));
  }
  
  private static <T extends EfValue> ExecutableMethod binaryMethod(Class<T> operandsClass, BiFunction<T, T, EfValue> function) {
    return method(stack -> {
      T lhs = operandsClass.cast(stack.peekArg(0));
      T rhs = operandsClass.cast(stack.peekArg(1));
      EfValue result = function.apply(lhs, rhs);
      stack.push(result);
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

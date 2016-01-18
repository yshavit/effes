package com.yuvalshavit.effes.interpreter;

import com.yuvalshavit.effes.compile.node.BuiltInMethodsFactory;
import com.yuvalshavit.effes.compile.TypeRegistry;

import java.io.PrintStream;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.LongBinaryOperator;
import java.util.stream.Stream;

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

  @Override
  public ExecutableMethod sprintf() {
    return method(stack -> {
      EfValue formatObj = stack.peekArg(0);
      EfValue args = stack.peekArg(1);
      EfValue.StringValue format = (EfValue.StringValue) formatObj;
      Object[] toArray = Stream.of(listToArray(args)).map(EfValue::getUserVisibleString).toArray(EfValue[]::new);
      String result = String.format(format.getString(), toArray);
      stack.push(EfValue.of(result));
    });
  }

  private EfValue[] listToArray(final EfValue head) {
    // assume that we either have a Head(T, tail) or an Empty.
    int nArgs = 0;
    for (List<EfValue> node = head.getState(); node.size() != 0; node = node.get(1).getState()) {
      assert node.size() == 0 || node.size() == 2 : "not a List: " + head;
      ++nArgs;
    }
    EfValue[] result = new EfValue[nArgs];
    int index = 0;
    for (List<EfValue> node = head.getState(); node.size() != 0; node = node.get(1).getState()) {
      assert node.size() == 0 || node.size() == 2 : "not a List: " + head;
      result[index++] = node.get(0);
    }
    return result;
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

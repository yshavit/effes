package com.yuvalshavit.effes.compile;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface BuiltInMethodsFactory<T> {
  @BuiltInMethod(name = "print", resultType = "Void", args = "True")
  public T print();

  default void addTo(TypeRegistry typeRegistry, MethodsRegistry<? super T> outRegistry) {
    for (Method m : BuiltInMethodsFactory.class.getDeclaredMethods()) {
      BuiltInMethod meta = m.getAnnotation(BuiltInMethod.class);
      if (meta != null) {
        Type returnType = m.getGenericReturnType();
        if (!(returnType instanceof TypeVariable) || !"T".equals(((TypeVariable) returnType).getName())) {
          throw new AssertionError("BuildInMethod must be parameterized to T");
        }
        Object raw;
        try {
          raw = m.invoke(this);
        } catch (Exception e) {
          throw new AssertionError(e); // the method is declared in this interface, so it must be public and invokable
        }
        @SuppressWarnings("unchecked")
        T casted = (T) raw; // we know this works because of the above checks of getGenericReturnType
        List<SimpleType> args = Stream.of(meta.args()).map(typeRegistry::getSimpleType).collect(Collectors.toList());
        SimpleType resultType = typeRegistry.getSimpleType(meta.resultType());
        outRegistry.registerTopLevelMethod(meta.name(), new EfMethod<>(args, resultType, casted));
      }
    }
  }
}

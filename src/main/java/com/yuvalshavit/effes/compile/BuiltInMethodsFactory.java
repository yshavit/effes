package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Pair;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.function.Function;
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
        TypeResolver resolver = new TypeResolver(typeRegistry, CompileErrors.throwing);
        EfArgs.Builder args = new EfArgs.Builder(CompileErrors.throwing);
        Function<String, Pair<Token, EfType>> typeParser = s -> {
          EffesParser parser = ParserUtils.createParser(s);
          EffesParser.TypeContext parsedType = parser.type();
          EfType type = resolver.apply(parsedType);
          return new Pair<>(parsedType.getStart(), type);
        };
        Stream.of(meta.args()).forEach(s -> {
          Pair<Token, EfType> parse = typeParser.apply(s);
          args.add(parse.a, null, parse.b);
        });
        EfType resultType = typeParser.apply(meta.resultType()).b;
        outRegistry.registerTopLevelMethod(meta.name(), new EfMethod<>(args.build(), resultType, casted));
      }
    }
  }
}

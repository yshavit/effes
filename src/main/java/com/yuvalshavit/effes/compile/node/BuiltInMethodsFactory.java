package com.yuvalshavit.effes.compile.node;

import com.google.common.collect.Sets;
import com.yuvalshavit.effes.compile.MethodsRegistry;
import com.yuvalshavit.effes.compile.TypeRegistry;
import com.yuvalshavit.effes.compile.TypeResolver;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Pair;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface BuiltInMethodsFactory<T> {
  @BuiltInMethod(name = "debugPrint", resultType = "Void", args = "T", generics = {"T"})
  T print();
  
  @BuiltInMethod(name = "+", resultType = "Int", args = "Int", targets = "IntValue | IntZero")
  T addInt();

  @BuiltInMethod(name = "-", resultType = "Int", args = "Int", targets = "IntValue | IntZero")
  T subInt();

  @BuiltInMethod(name = "*", resultType = "Int", args = "Int", targets = "IntValue | IntZero")
  T multInt();

  @BuiltInMethod(name = "/", resultType = "Int", args = "IntValue", targets = "IntValue | IntZero")
  T divInt();

  default void addTo(TypeRegistry typeRegistry, MethodsRegistry<? super T> outRegistry, CompileErrors errs) {
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
        TypeResolver resolver = new TypeResolver(typeRegistry, errs, null); // TODO need better than null when handling generic built-ins?
        EfArgs.Builder args = new EfArgs.Builder(errs);
        Function<String, Pair<Token, EfType>> typeParser = s -> {
          EffesParser parser = ParserUtils.createParser(String.format("(%s)", s));
          EffesParser.TypeContext parsedType = parser.type();
          assert parsedType.getStop().getStopIndex() - 1 == s.length() : "invalid type declaration: " + s;
            EfType type = resolver.apply(parsedType);
          return new Pair<>(parsedType.getStart(), type);
        };
        String methodName = meta.name();
        EfMethod.Id id = new EfMethod.Id(methodName);
        Set<String> genericsSet = Sets.newHashSet(meta.generics());
        List<EfType.GenericType> genericsList = Stream.of(meta.generics()).map(g -> new EfType.GenericType(g, id)).collect(Collectors.toList());
        if (genericsList.size() != genericsSet.size()) {
          throw new AssertionError("duplicate generics: " + genericsList);
        }
        Stream.of(meta.args()).forEach(s -> {
          Pair<Token, EfType> parse;
          if (genericsSet.contains(s)) {
            parse = new Pair<>(null, new EfType.GenericType(s, id));
          } else {
            parse = typeParser.apply(s);
          }
          args.add(parse.a, null, parse.b);
        });
        EfType resultType = typeParser.apply(meta.resultType()).b;
        Collection<String> targets = Stream.of(meta.targets().split(" *\\| *")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        if (targets.isEmpty()) {
          EfMethod<T> method = new EfMethod<>(id, genericsList, args.build(), resultType, casted);
          outRegistry.registerMethod(MethodId.topLevel(methodName), method);
        } else {
          if (meta.generics().length > 0) {
            errs.add(null, "unsupported generics on builtin instance method (this is a compiler error): " + methodName);
          } else {
            for (String target : targets) {
              EfType.SimpleType simpleType = typeRegistry.getSimpleType(target);
              if (simpleType == null) {
                errs.add(null, String.format("builtin instance method %s on unknown type %s (this is a compiler error)", methodName, target));
              } else if (!simpleType.getGenericsDeclr().isEmpty()) {
                errs.add(null, String.format("builtin instance method %s on generic type %s (this is a compiler error)", methodName, target));
              } else {
                EfArgs.Builder argsForInstance = new EfArgs.Builder(errs);
                argsForInstance.add(null, EfVar.THIS_VAR_NAME, simpleType);
                argsForInstance.addAllFrom(args);
                EfMethod<T> method = new EfMethod<>(id, genericsList, argsForInstance.build(), resultType, casted);
                outRegistry.registerMethod(MethodId.of(simpleType, methodName), method);
              }
            }
          }
        }
      }
    }
  }
}

package com.yuvalshavit.effes.base;

import javax.annotation.Nullable;
import java.util.List;

public interface MutableTypeRegistry extends TypeRegistery {
  Type.SimpleType register(String name);
  void registerSubtype(Type.SimpleType supertype, Type.SimpleType subtype);
  void registerMethod(Type target, String name, Type returnType, List<Type> argTypes, @Nullable EfMethod method);
  void freeze();
}

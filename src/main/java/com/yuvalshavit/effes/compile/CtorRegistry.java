/*
 * Copyright © 2015 Sqrrl Data, Inc. All Rights Reserved.
 *
 * You may not use, distribute or modify this code except
 * with the express written authorization of Sqrrl Data,
 * Inc. under the terms of the Sqrrl Enterprise license.
 */
package com.yuvalshavit.effes.compile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.effes.compile.node.EfVar;

public class CtorRegistry {
  
  private final Map<EfType.SimpleType, List<EfVar>> argsByType = new HashMap<>();
  
  @Nonnull
  public List<EfVar> get(EfType.SimpleType type, Function<EfType.GenericType, EfType> reification) {
    List<EfVar> args = argsByType.get(type.getGeneric());
    Preconditions.checkArgument(args != null, "unknown type: " + type);
    return args.stream().map(v -> v.reify(reification)).collect(Collectors.toList());
  }

  @Nullable
  public EfVar getArgByName(EfType.SimpleType type, String name, Function<EfType.GenericType, EfType> reification) {
    for (EfVar arg : get(type, t -> t)) {
      if (arg.getName().equals(name)) {
        return arg.reify(reification);
      }
    }
    return null;
  }
  
  public void setCtorArgs(EfType.SimpleType type, List<EfVar> ctorArgs) {
    Preconditions.checkArgument(type == type.getGeneric(), "can only set ctor args for unreified types");
    Preconditions.checkNotNull(ctorArgs);
    if (argsByType.containsKey(type)) {
      // TypesRegistry will complain about the dupe; we don't need to
      return;
    }
    for (int pos = 0; pos < ctorArgs.size(); ++pos) {
      EfVar arg = ctorArgs.get(pos);
      if (pos != arg.getArgPosition() || !arg.isArg()) {
        throw new IllegalArgumentException("invalid args list: " + ctorArgs);
      }
    }
    argsByType.put(type, ImmutableList.copyOf(ctorArgs));
  }

}

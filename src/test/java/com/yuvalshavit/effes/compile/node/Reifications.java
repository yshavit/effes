package com.yuvalshavit.effes.compile.node;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.base.Functions;
import com.yuvalshavit.util.EfFunctions;

public class Reifications {
  private Reifications() {}
  
  public static EfType.SimpleType reify(EfType.SimpleType target, EfType onlyGenericReification) {
    EfType.GenericType generic = onlyGenericOf(target);
    Map<EfType.GenericType, EfType> map = Collections.singletonMap(generic, onlyGenericReification);
    return target.reify(EfFunctions.fromGuava(Functions.forMap(map)));
  }

  public static EfType.GenericType onlyGenericOf(EfType.SimpleType target) {
    List<EfType> targetParams = target.getGeneric().getParams();
    checkArgument(targetParams.size() == 1, "target %s must have exactly one generic parameter", target);
    return ((EfType.GenericType) targetParams.get(0));
  }
}

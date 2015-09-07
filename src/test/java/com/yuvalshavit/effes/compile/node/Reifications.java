package com.yuvalshavit.effes.compile.node;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.common.base.Functions;
import com.yuvalshavit.util.EfFunctions;

public class Reifications {
  private Reifications() {}

  public static SingleGenericReifier reifyOnlyGenericOf(EfType.SimpleType target) {
    return new SingleGenericReifier(target);
  }

  public static EfType.GenericType onlyGenericOf(EfType.SimpleType target) {
    List<EfType> targetParams = target.getGeneric().getParams();
    checkArgument(targetParams.size() == 1, "target %s must have exactly one generic parameter", target);
    return ((EfType.GenericType) targetParams.get(0));
  }
  
  public static EfType.GenericType genericByName(EfType.SimpleType target, String name) {
    checkNotNull(name);
    return target.getParams().stream()
      .flatMap(EfFunctions.instancesOf(EfType.GenericType.class))
      .filter(g -> name.equals(g.getName()))
      .findFirst()
      .orElse(null);
  }
  
  public static GenericReifier reify(EfType.SimpleType target) {
    return new GenericReifier(target);
  }
  
  public static class GenericReifier {
    private final Map<EfType.GenericType, EfType> reifications = new TreeMap<>(Comparator.comparing(EfType.GenericType::getName));
    private final EfType.SimpleType target;

    private GenericReifier(EfType.SimpleType target) {
      this.target = target;
    }
    
    public GenericReifier with(String name, EfType reification) {
      EfType.GenericType generic = genericByName(target, name);
      if (reifications.containsKey(generic)) {
        throw new IllegalStateException("already have mapping for " + name);
      }
      reifications.put(generic, reification);
      return this;
    }
    
    public EfType.SimpleType get() {
      return target.reify(EfFunctions.fromGuava(Functions.forMap(reifications)));
    }
  }
  
  public static class SingleGenericReifier {

    private final EfType.SimpleType target;

    private SingleGenericReifier(EfType.SimpleType target) {
      this.target = target;
    }
    
    public EfType.SimpleType to(EfType reification) {
      EfType.GenericType generic = onlyGenericOf(target);
      Map<EfType.GenericType, EfType> map = Collections.singletonMap(generic, reification);
      return target.reify(EfFunctions.fromGuava(Functions.forMap(map)));
    }
  }
}

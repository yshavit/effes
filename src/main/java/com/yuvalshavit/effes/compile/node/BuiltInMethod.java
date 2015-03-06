package com.yuvalshavit.effes.compile.node;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@interface BuiltInMethod {
  String name();
  String resultType();
  String[] args();
  String[] generics() default {};
  String targets() default "";
}

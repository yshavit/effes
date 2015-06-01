package com.yuvalshavit.effes.compile.node;

import java.util.function.Function;
import java.util.function.Supplier;

class EfTypeHandlerBuilders {
  
  public static <T> SimpleHandler<T> create(Class<T> cls) {
    return new HandlerBuilder<>();
  }
  
  public interface SimpleHandler<T> {
    DisjunctionHandler<T> onSimple(Function<? super EfType.SimpleType, ? extends T> f);
  }
  
  public interface DisjunctionHandler<T> {
    GenericHandler<T> onDisjunction(Function<? super EfType.DisjunctiveType, ? extends T> f);
  }
  
  public interface GenericHandler<T> {
    VoidHandler<T> onGeneric(Function<? super EfType.GenericType, ? extends T> f);
  }
  
  public interface VoidHandler<T> {
    UnknownHandler<T> onVoid(Supplier<T> supplier);
  }
  
  public interface UnknownHandler<T> {
    Function<EfType, ? extends T> onUnknown(Supplier<T> supplier);
  }
  
  private static class HandlerBuilder<T> implements
    Function<EfType, T>,
    SimpleHandler<T>,
    DisjunctionHandler<T>,
    GenericHandler<T>,
    VoidHandler<T>,
    UnknownHandler<T>
  {

    private Function<? super EfType.SimpleType, ? extends T> onSimple;
    private Function<? super EfType.GenericType, ? extends T> onGeneric;
    private Function<? super EfType.DisjunctiveType, ? extends T> onDisjunction;
    private Supplier<T> onVoid;
    private Supplier<T> onUnknown;

    @Override
    public DisjunctionHandler<T> onSimple(Function<? super EfType.SimpleType, ? extends T> f) {
      this.onSimple = f;
      return this;
    }

    @Override
    public GenericHandler<T> onDisjunction(Function<? super EfType.DisjunctiveType, ? extends T> f) {
      this.onDisjunction = f;
      return this;
    }

    @Override
    public VoidHandler<T> onGeneric(Function<? super EfType.GenericType, ? extends T> f) {
      this.onGeneric = f;
      return this;
    }

    @Override
    public HandlerBuilder<T> onUnknown(Supplier<T> supplier) {
      this.onUnknown = supplier;
      return this;
    }

    @Override
    public UnknownHandler<T> onVoid(Supplier<T> supplier) {
      this.onVoid = supplier;
      return this;
    }

    public T apply(EfType type) {
      return type.handle(new EfTypeHandler<T>() {
        @Override
        public T simple(EfType.SimpleType type) {
          return onSimple.apply(type);
        }

        @Override
        public T disjunction(EfType.DisjunctiveType type) {
          return onDisjunction.apply(type);
        }

        @Override
        public T generic(EfType.GenericType type) {
          return onGeneric.apply(type);
        }

        @Override
        public T unknown() {
          return onUnknown.get();
        }

        @Override
        public T voidType() {
          return onVoid.get();
        }
      });
    }
  }
  
  
}

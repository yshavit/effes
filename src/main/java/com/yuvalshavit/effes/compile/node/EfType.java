package com.yuvalshavit.effes.compile.node;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.yuvalshavit.util.Dispatcher;
import com.yuvalshavit.util.EfCollections;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class EfType {

  public static final Function<GenericType,EfType> KEEP_GENERIC = t -> t;
  
  public static final EfType UNKNOWN = new UnknownType(UnknownType.Variant.UNKNOWN);
  public static final EfType VOID = new UnknownType(UnknownType.Variant.VOID);
  public static final Comparator<EfType> comparator = new EfTypeComparator();

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();

  public abstract Collection<SimpleType> simpleTypes();
  
  public abstract <T> T handle(EfTypeHandler<? extends T> handler);

  public boolean isFakeType() {
    return getClass().equals(UnknownType.class);
  }

  public static boolean sameRawType(EfType first, EfType second) {
    return (first instanceof SimpleType)
      && (second instanceof SimpleType)
      && ((SimpleType) first).getGeneric().equals(((SimpleType) second).getGeneric());
  }
  
  private EfType() {}

  /**
   * Returns whether this type is a "supertype" of the other; that is, whether a reference of the other type
   * is fully contained within this one.
   *
   * For instance, if {@code Boolean = True | False}, then {@code Boolean.contains(True)}.
   */
  public abstract boolean contains(EfType other);

  @Override
  public abstract String toString();

  public abstract EfType reify(Function<GenericType, EfType> reification);

  private static final class UnknownType extends EfType {
    private enum Variant {
      UNKNOWN("unknown type") {
        @Override
        <T> T handle(EfTypeHandler<? extends T> handler) {
          return handler.unknown();
        }
      },
      VOID("no result type") {
        @Override
        <T> T handle(EfTypeHandler<? extends T> handler) {
          return handler.voidType();
        }
      },
      ;

      private final String description;
      

      Variant(String description) {
        this.description = description;
      }
      
      abstract <T> T handle(EfTypeHandler<? extends T> handler);
    }
    private final Variant variant;

    private UnknownType(Variant variant) {
      this.variant = variant;
    }

    @Override
    public boolean contains(EfType other) {
      return false;
    }

    @Override
    public Collection<SimpleType> simpleTypes() {
      return Collections.emptySet();
    }

    @Override
    public EfType reify(Function<GenericType, EfType> reification) {
      return this;
    }

    @Override
    public <T> T handle(EfTypeHandler<? extends T> handler) {
      return variant.handle(handler);
    }

    @Override
    public String toString() {
      return variant.description;
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this; // assume singleton
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }
  }

  public static final class SimpleType extends EfType {
    private final String name;
    private final List<EfType> params;
    private final SimpleType genericForm;
    private List<CtorArg> ctorArgs; // TODO find a way to make this be final

    public SimpleType(String name, List<String> params) {
      this.name = name;
      this.params = ImmutableList.copyOf(params.stream().map(s -> new GenericType(s, this)).collect(Collectors.toList()));
      this.genericForm = this;
    }

    private SimpleType(String name, List<EfType> params, List<CtorArg> args, SimpleType genericForm) {
      this.name = checkNotNull(name);
      this.params = checkNotNull(params);
      this.genericForm = checkNotNull(genericForm);
      this.ctorArgs = checkNotNull(args);
    }
    
    public String getName() {
      return name;
    }

    public List<EfType> getParams() {
      return params;
    }

    @Override
    public EfType.SimpleType reify(Function<GenericType, EfType> reificationFunc) {
      checkNotNull(ctorArgs); // check it eagerly, since the resulting list is computed lazily
      if (reificationFunc == KEEP_GENERIC) {
        return this;
      }
      List<EfType> reifiedParams = ImmutableList.copyOf(Lists.transform(params, p -> p.reify(reificationFunc)));
      List<CtorArg> reifiedArgs = Lists.transform(ctorArgs, arg -> arg.reify(reificationFunc));
      return new SimpleType(name, reifiedParams, reifiedArgs, genericForm);
    }

    @Override
    public Collection<SimpleType> simpleTypes() {
      return Collections.singleton(this);
    }

    @Override
    public <T> T handle(EfTypeHandler<? extends T> handler) {
      return handler.simple(this);
    }

    public SimpleType getGeneric() {
      return genericForm;
    }

    public List<GenericType> getGenericsDeclr() {
      List raw = params;
      @SuppressWarnings("unchecked")
      List<GenericType> generic = (List<GenericType>) raw;
      for (Object elem : raw) {
        assert elem instanceof GenericType : String.format("not a %s: %s", GenericType.class.getSimpleName(), elem);
      }
      return generic;
    } 
    
    /**
     * Returns the simple type's name and parameters.
     */
    @Override
    public String toString() {
      // If this is the generic form, we want to print only the names, not the full toString (which would include this object's toString from the GenericName,
      // and thus cause a stack overflow).
      if (this == genericForm) {
        return params.isEmpty()
          ? name
          : name + getGenericsDeclr().stream().map(GenericType::getName).collect(Collectors.toList());
      } else {
        return params.isEmpty()
          ? name
          : name + params;
      }
    }

    @Override
    public boolean contains(EfType other) {
      if (other.getClass() != SimpleType.class) {
        return false;
      }
      SimpleType otherSimple = (SimpleType) other;
      if (!name.equals(otherSimple.name)) {
        return false;
      }
      assert params.size() == otherSimple.params.size() : String.format("%d != %d", params.size(), otherSimple.params.size());
      for (int i = 0, len = params.size(); i < len; ++i) {
        EfType myParam = params.get(i);
        EfType otherParam = otherSimple.params.get(i);
        if (!myParam.contains(otherParam)) { // TODO equality/contains/etc implies covariance rules! Need to flush these out!
          return false;
        }
      }
      return true;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;

      SimpleType that = (SimpleType) o;

      return (genericForm == that.genericForm) && params.equals(that.params);
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }

    public Function<GenericType, EfType> getReification() {
      List<EfType> genericsDeclr = genericForm.params;
      return g -> {
        int idx = genericsDeclr.indexOf(g); // TODO O(N)
        if (idx < 0) {
          throw new IllegalArgumentException("unrecognized generic " + g); // TODO is this right?
        }
        return params.get(idx);
      };
    }
    
    @Nonnull
    public List<CtorArg> getArgs() {
      checkState(ctorArgs != null, "args not set");
      return ctorArgs;
    }
    
    public void setCtorArgs(List<CtorArg> ctorArgs) {
      checkNotNull(ctorArgs);
      if (this.ctorArgs != null) {
        // TypesRegistry will complain about the dupe; we don't need to (?)
        return; // TODO try to verify that we ever hit this case
      }
      this.ctorArgs = ImmutableList.copyOf(ctorArgs);
    }
    
    @Nullable
    public CtorArg getArgByName(String name) {
      for (CtorArg arg : getArgs()) {
        if (arg.name().equals(name)) {
          return arg;
        }
      }
      return null;
    }

    public SimpleType withCtorArgs(List<EfType> newArgs) {
      List<CtorArg> currentVars = getArgs();
      checkArgument(newArgs.size() == currentVars.size(), "mismatch in number of args between %s and %s", currentVars, newArgs);
      Iterable<CtorArg> newVars = EfCollections.zipF(currentVars, newArgs, CtorArg::castTo);
      return new SimpleType(name, params, Lists.newArrayList(newVars), genericForm);
    }
    
    public SimpleType withCtorArgs(EfType... newArgs) {
      return withCtorArgs(Arrays.asList(newArgs));
    }
  }

  public static EfType disjunction(EfType t1, EfType... rest) {
    return disjunction(ImmutableList.<EfType>builder().add(t1).add(rest).build());
  }

  public static EfType disjunction(Collection<? extends EfType> options) {
    if (options.isEmpty()) {
      throw new IllegalArgumentException("can't disjoin an empty set");
    }
    Optional<? extends EfType> maybeShortCircuit = options.stream().filter(EfType::isFakeType).findFirst();
    if (maybeShortCircuit.isPresent()) {
      return maybeShortCircuit.get();
    }
    DisjunctiveType d = new DisjunctiveType(options);
    return d.options.size() == 1
      ? d.options.first()
      : d;
  }

  public static final class DisjunctiveType extends EfType {
    private final SortedSet<EfType> options;

    private DisjunctiveType(Collection<? extends EfType> options) {
      // flatten them out
      SortedSet<EfType> optionsBuilder = new TreeSet<>(comparator);
      EfTypeDispatch dispatch = new EfTypeDispatch() {
        @Override
        public boolean accept(SimpleType type) {
          return optionsBuilder.add(type);
        }

        @Override
        public boolean accept(DisjunctiveType type) {
          type.options.forEach(this::accept);
          return true;
        }

        @Override
        public boolean accept(GenericType type) {
          return optionsBuilder.add(type);
        }

        @Override
        public boolean accept(UnknownType type) {
          return optionsBuilder.add(type);
        }
      };
      options.forEach(dispatch::accept);
      this.options = optionsBuilder;
    }

    public Set<EfType> getAlternatives() { // TODO how is this different from simpleTypes? It came in later, so maybe this is obsolete?
      return Collections.unmodifiableSet(options);
    }

    @Override
    public EfType reify(Function<GenericType, EfType> reification) {
      return disjunction(options.stream().map(r -> r.reify(reification)).collect(Collectors.toSet()));
    }

    @Override
    public boolean contains(EfType other) {
      return new EfTypeDispatch() {
        @Override
        public boolean accept(SimpleType type) {
          for (EfType option : options) {
            if (option.contains(other)) {
              return true;
            }
          }
          return false;
        }

        @Override
        public boolean accept(DisjunctiveType type) {
          Collection<EfType> otherComponents = type.options;
          return options.containsAll(otherComponents);
        }

        @Override
        public boolean accept(GenericType type) {
          return false;
        }

        @Override
        public boolean accept(UnknownType type) {
          return false;
        }
      }.accept(other);
    }

    @Override
    public Collection<SimpleType> simpleTypes() {
      return options.stream().flatMap(t -> t.simpleTypes().stream()).collect(Collectors.toList());
    }

    @Override
    public <T> T handle(EfTypeHandler<? extends T> handler) {
      return handler.disjunction(this);
    }

    @Override
    public String toString() {
      return Joiner.on(" | ").join(options);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      DisjunctiveType that = (DisjunctiveType) o;
      return options.equals(that.options);

    }

    @Override
    public int hashCode() {
      return options.hashCode();
    }
  }
  
  public static class GenericType extends EfType {
    private final String name;
    private final Object owner;

    public GenericType(String name, Object owner) {
      this.name = checkNotNull(name);
      this.owner = checkNotNull(owner);
    }
    
    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;
      GenericType that = (GenericType) o;
      return name.equals(that.name) && owner.equals(that.owner);
    }

    @Override
    public int hashCode() {
      int result = name.hashCode();
      result = 31 * result + owner.hashCode();
      return result;
    }

    @Override
    public EfType reify(Function<GenericType, EfType> reification) {
      EfType reified = reification.apply(this);
      if (reified == null) {
        throw new IllegalArgumentException("can't reify " + this);
      }
      return reified;
    }

    @Override
    public Collection<SimpleType> simpleTypes() {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> T handle(EfTypeHandler<? extends T> handler) {
      return handler.generic(this);
    }

    @Override
    public boolean contains(EfType other) {
      return equals(other);
    }

    @Override
    public String toString() {
      return name + " on " + owner;
    }

    public String getName() {
      return name;
    }

    public Object getOwner() {
      return owner;
    }
  }

  private static abstract class EfTypeDispatch {
    static final Dispatcher<EfTypeDispatch, EfType, Boolean> dispatcher
      = Dispatcher.builder(EfTypeDispatch.class, EfType.class, Boolean.class)
      .put(SimpleType.class, EfTypeDispatch::accept)
      .put(DisjunctiveType.class, EfTypeDispatch::accept)
      .put(GenericType.class, EfTypeDispatch::accept)
      .put(UnknownType.class, EfTypeDispatch::accept)
      .build((me, t) -> {
        throw new AssertionError(t);
      });

    public boolean accept(EfType type) {
      return type != null
        ? dispatcher.apply(this, type)
        : accept(UNKNOWN);
    }

    public abstract boolean accept(SimpleType type);
    public abstract boolean accept(DisjunctiveType type);
    public abstract boolean accept(GenericType type);
    public abstract boolean accept(UnknownType type);
  }

  private static class EfTypeComparator implements Comparator<EfType> {
    private enum TypeOrdinal {
      NULL,
      UNKNOWN,
      SIMPLE,
      GENERIC,
      DISJUNCTIVE,
    }
    static final Dispatcher<?, EfType, TypeOrdinal> typeClassOrdinal
      = Dispatcher.builder(Object.class, EfType.class, TypeOrdinal.class)
      .put(SimpleType.class, (d, i) -> TypeOrdinal.SIMPLE)
      .put(DisjunctiveType.class, (d, i) -> TypeOrdinal.DISJUNCTIVE)
      .put(GenericType.class, (d, i) -> TypeOrdinal.GENERIC)
      .put(UnknownType.class, (d, i) -> TypeOrdinal.UNKNOWN)
      .build((me, t) -> {throw new AssertionError(t); });

    @Override
    public int compare(EfType o1, EfType o2) {
      // First, compare type ordinals
      TypeOrdinal o1Ordinal = typeOrdinal(o1);
      TypeOrdinal o2Ordinal = typeOrdinal(o2);
      int ordinalCmp = o1Ordinal.compareTo(o2Ordinal);
      if (ordinalCmp != 0) {
        return ordinalCmp;
      }
      switch (o1Ordinal) {
      case NULL:
        return 0; // null == null
      case UNKNOWN:
        UnknownType u1 = (UnknownType) o1;
        UnknownType u2 = (UnknownType) o2;
        return u1.variant.compareTo(u2.variant);
      case SIMPLE:
        SimpleType s1 = (SimpleType) o1;
        SimpleType s2 = (SimpleType) o2;
        return s1.toString().compareTo(s2.toString());
      case GENERIC:
        GenericType g1 = (GenericType) o1;
        GenericType g2 = (GenericType) o2;
        return g1.name.compareTo(g2.name);
      case DISJUNCTIVE:
        Iterator<EfType> d1Iter = ((DisjunctiveType) o1).options.iterator();
        Iterator<EfType> d2Iter = ((DisjunctiveType) o2).options.iterator();
        while (d1Iter.hasNext() && d2Iter.hasNext()) {
          EfType e1 = d1Iter.next();
          EfType e2 = d2Iter.next();
          int cmp = compare(e1, e2);
          if (cmp != 0) {
            return cmp;
          }
        }
        // all the elements were the same, so which is longer?
        if (d1Iter.hasNext()) {
          return 1; // d1 was longer, so d1 > d2
        }
        return d2Iter.hasNext()
          ? -1 // d2 was longer
          : 0;
      default:
        throw new AssertionError("unknown type ordinal: " + o1Ordinal);
      }
    }

    private TypeOrdinal typeOrdinal(EfType o) {
      if (o == null) {
        return TypeOrdinal.NULL;
      }
      return typeClassOrdinal.apply(null, o);
    }

  }
}

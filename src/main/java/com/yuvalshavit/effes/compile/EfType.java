package com.yuvalshavit.effes.compile;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.yuvalshavit.util.Dispatcher;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public abstract class EfType {

  public static final EfType UNKNOWN = new UnknownType("unknown type");
  public static final EfType VOID = new UnknownType("no result type");
  public static final Comparator<EfType> comparator = new EfTypeComparator();

  @Override
  public abstract boolean equals(Object obj);
  @Override
  public abstract int hashCode();

  public boolean isFakeType() {
    return getClass().equals(UnknownType.class);
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

  private static final class UnknownType extends EfType {
    private final String name;

    private UnknownType(String name) {
      this.name = name;
    }

    @Override
    public boolean contains(EfType other) {
      return false;
    }

    @Override
    public String toString() {
      return name;
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
    private List<EfVar> args;

    public SimpleType(String name) {
      assert name != null;
      this.name = name;
    }

    public String getName() {
      return name;
    }

    @Nonnull
    public List<EfVar> getArgs() {
      return args != null
        ? args
        : ImmutableList.of();
    }

    public void setArgs(List<EfVar> args) {
      if (args == null) {
        throw new IllegalStateException("args already set" + this.args);
      }
      for (int pos = 0; pos < args.size(); ++pos) {
        EfVar arg = args.get(pos);
        if (pos != arg.getArgPosition() || !arg.isArg()) {
          throw new IllegalArgumentException("invalid args list: " + args);
        }
      }
      this.args = ImmutableList.copyOf(args);
    }

    /**
     * Returns just the simple type's name &mdash; not its args.
     *
     * Printing the args has some problems. If the type's args are somewhat complex, this recursion would get
     * unwieldy: Foo(hello: World(solarSystem: Star(name: String))), for instance. In the extreme case, for recursive
     * types we'd have an infinitely long string: Cons(head: E, tail: Empty | Cons(head: E, tail : Empty | Cons...)).
     *
     * So we can't print out the arg's types. What about just the names? "Cons(head, tail)" for instance. That'd be
     * okay, but the half-loaf approach isn't very compelling. Instead, if someone wants to write custom code to print
     * out something reasonable for a given context, they can (for instance, recursing only one level down).
     */
    @Override
    public String toString() {
      return name;
    }

    @Override
    public boolean contains(EfType other) {
      return equals(other);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      SimpleType that = (SimpleType) o;
      return name.equals(that.name);
    }

    @Override
    public int hashCode() {
      return name.hashCode();
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
        public boolean accept(UnknownType type) {
          return optionsBuilder.add(type);
        }
      };
      options.forEach(dispatch::accept);
      this.options = optionsBuilder;
    }

    public Set<EfType> getAlternatives() {
      return Collections.unmodifiableSet(options);
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
        public boolean accept(UnknownType type) {
          return false;
        }
      }.accept(other);
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

  private static abstract class EfTypeDispatch {
    static final Dispatcher<EfTypeDispatch, EfType, Boolean> dispatcher
      = Dispatcher.builder(EfTypeDispatch.class, EfType.class, Boolean.class)
      .put(SimpleType.class, EfTypeDispatch::accept)
      .put(DisjunctiveType.class, EfTypeDispatch::accept)
      .put(UnknownType.class, EfTypeDispatch::accept)
      .build((me, t) -> { throw new AssertionError(t); });

    public boolean accept(EfType type) {
      return type != null
        ? dispatcher.apply(this, type)
        : accept(UNKNOWN);
    }

    public abstract boolean accept(SimpleType type);
    public abstract boolean accept(DisjunctiveType type);
    public abstract boolean accept(UnknownType type);
  }

  private static class EfTypeComparator implements Comparator<EfType> {
    private enum TypeOrdinal {
      NULL,
      UNKNOWN,
      SIMPLE,
      DISJUNCTIVE
    }
    static final Dispatcher<?, EfType, TypeOrdinal> typeClassOrdinal
      = Dispatcher.builder(Object.class, EfType.class, TypeOrdinal.class)
      .put(SimpleType.class, (d, i) -> TypeOrdinal.SIMPLE)
      .put(DisjunctiveType.class, (d, i) -> TypeOrdinal.DISJUNCTIVE)
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
      case UNKNOWN:
        return 0; // null == null, UNKNOWN == UNKNOWN
      case SIMPLE:
        SimpleType s1 = (SimpleType) o1;
        SimpleType s2 = (SimpleType) o2;
        return s1.toString().compareTo(s2.toString());
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

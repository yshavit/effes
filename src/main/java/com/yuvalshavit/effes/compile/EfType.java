package com.yuvalshavit.effes.compile;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.yuvalshavit.util.Dispatcher;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public abstract class EfType {

  public static final EfType UNKNOWN = new UnknownType();

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
    @Override
    public boolean contains(EfType other) {
      return false;
    }

    @Override
    public String toString() {
      return "<unknown type>";
    }
  }

  public static final class SimpleType extends EfType {
    private final String name;

    public SimpleType(String name) {
      assert name != null;
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }

    @Override
    public boolean contains(EfType other) {
      return equals(other);
    }
  }

  public static final class DisjunctiveType extends EfType {
    private final Collection<EfType> options;

    public DisjunctiveType(Collection<EfType> options) {
      // flatten them out
      Set<EfType> flattened = new HashSet<>(options.size());
      EfTypeDispatch dispatch = new EfTypeDispatch() {
        @Override
        public boolean accept(SimpleType type) {
          return flattened.add(type);
        }

        @Override
        public boolean accept(DisjunctiveType type) {
          type.options.forEach(this::accept);
          return true;
        }

        @Override
        public boolean accept(UnknownType type) {
          return flattened.add(type);
        }
      };
      options.forEach(dispatch::accept);
      this.options = ImmutableSet.copyOf(flattened);
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
      return String.format("(%s)", Joiner.on(" | ").join(options));
    }
  }

  private static abstract class EfTypeDispatch {
    static final Dispatcher<EfTypeDispatch, EfType, Boolean> dispatcher
      = Dispatcher.builder(EfTypeDispatch.class, EfType.class, Boolean.class)
      .put(SimpleType.class, EfTypeDispatch::accept)
      .put(DisjunctiveType.class, EfTypeDispatch::accept)
      .put(UnknownType.class, EfTypeDispatch::accept)
      .build();

    public boolean accept(EfType type) {
      return type != null
        ? dispatcher.apply(this, type)
        : accept(UNKNOWN);
    }

    public abstract boolean accept(SimpleType type);
    public abstract boolean accept(DisjunctiveType type);
    public abstract boolean accept(UnknownType type);
  }
}

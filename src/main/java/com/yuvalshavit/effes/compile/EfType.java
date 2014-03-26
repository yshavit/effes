package com.yuvalshavit.effes.compile;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public abstract class EfType {

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
      flatAdd(options, flattened);
      this.options = ImmutableSet.copyOf(flattened);
    }

    private void flatAdd(Collection<EfType> options, Set<EfType> flattened) {
      for (EfType option : options) {
        if (option instanceof SimpleType) {
          flattened.add(option);
        } else if (option instanceof DisjunctiveType) {
          Collection<EfType> innerOptions = ((DisjunctiveType) option).options;
          flatAdd(innerOptions, flattened);
        } else {
          throw new AssertionError(option);
        }
      }
    }

    @Override
    public boolean contains(EfType other) {
      if (other instanceof SimpleType) {
        for (EfType option : options) {
          if (option.contains(other)) {
            return true;
          }
        }
      } else if (other instanceof DisjunctiveType) {
        Collection<EfType> otherComponents = ((DisjunctiveType) other).options;
        return options.containsAll(otherComponents);
      } else {
        throw new AssertionError(other);
      }
      return false;
    }

    @Override
    public String toString() {
      return String.format("(%s)", Joiner.on(" | ").join(options));
    }
  }
}

package com.yuvalshavit.effes.compile.pmatch;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.yuvalshavit.util.EfCollections;
import com.yuvalshavit.util.EfFunctions;
import com.yuvalshavit.util.Equality;

import static com.yuvalshavit.util.EfCollections.mapList;

public abstract class PCase {

  @Nullable public abstract PCase minus(@Nonnull PCase type);
  public abstract <T> T handle(PCaseHandler<? extends T> handler);
  @Override public abstract int hashCode();
  @Override public abstract boolean equals(Object other);
  @Override public abstract String toString();

  private PCase() { }

  private static class Any extends PCase {
    private static final Equality<Any> equality = Equality.forClass(Any.class).with("name", Any::name).exactClassOnly();
    private final String name;

    public Any(String name) {
      this.name = name;
    }
    
    private String name() {
      return name;
    }

    @Nullable
    @Override
    public PCase minus(@Nonnull PCase type) {
      return none;
    }

    @Override
    public <T> T handle(PCaseHandler<? extends T> handler) {
      return handler.whenAny();
    }

    @Override
    public String toString() {
      return equality.toString(this);
    }

    @Override
    public int hashCode() {
      return equality.hash(this);
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object other) {
      return equality.areEqual(this, other);
    }
  }

  public static Any any(String name) {
    return new Any(name);
  }
  
  public static Any any() {
    return any("_");
  }
  
  public static final PCase none = new PCase() {
    @Nullable
    @Override
    public PCase minus(@Nonnull PCase type) {
      return none;
    }

    @Override
    public <T> T handle(PCaseHandler<? extends T> handler) {
      return handler.whenNone();
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object other) {
      return this == other;
    }

    @Override
    public String toString() {
      return "none";
    }
  };

  public static class Simple extends PCase {
    private static final Equality<Simple> equality = Equality.forClass(Simple.class).with("lhs", Simple::lhs).exactClassOnly();
    private final TypedValue lhs;

    public Simple(TypedValue lhs) {
      this.lhs = lhs;
    }

    @Nullable
    @Override
    public PCase minus(@Nonnull PCase rhs) {
      // "case lhs of rhs"
      return rhs.handle(new PCaseHandler<PCase>() {
        @Override
        public PCase whenAny() {
          return none;
        }

        @Override
        public PCase whenNone() {
          return null;
        }

        @Override
        public PCase whenSimple(TypedValue rhs) {
          // lhs and rhs are both simple. First, we want to make sure their types are the same. If they are, we need to recurse down each argument:
          // - if any of them return null, then it can't be matched. This is for instance if lhs::Pet(species: Dog | Cat, name: String) and rhs::Pet(Mouse, _)
          // - if they're all none, then the result is also none
          // - otherwise, return the Simple composed of the arguments
          // Additionally:
          // -- If the types are not equal, then we return null.
          // -- If the types are a "large domain" type, then we just return self (no additional info is carried forward).
          if (!lhs.type().equals(rhs.type())) {
            return none;
          }
          return lhs.handle(
            largeDomainLhs -> Simple.this,
            standardLhs -> rhs.handle(
              largeDomainValue -> {
                throw new AssertionError(String.format("lhs=%s, rhs=%s", lhs, rhs));
              },
              subtract(standardLhs, rhs)));
        }

        @Override
        public PCase whenDisjunction(Collection<Supplier<Simple>> alternatives) {
          // e.g. t: List[T] = Head[T] | Empty
          List<? extends Simple> altsList = mapList(alternatives, Supplier::get);

          if (altsList.stream().an)
          throw new UnsupportedOperationException(); // TODO distribute if possible, else null
        }
      });
      // In this case, lhs is just a single value, and  
    }

    @Override
    public <T> T handle(PCaseHandler<? extends T> handler) {
      return handler.whenSimple(lhs);
    }

    @Override
    public int hashCode() {
      return equality.hash(this);
    }
    
    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object other) {
      return equality.areEqual(this, other);
    }

    @Override
    public String toString() {
      return equality.toString(this);
    }

    private TypedValue lhs() {
      return lhs;
    }
  }

  public static class Disjunction extends PCase {
    private final Equality<Disjunction> equality = Equality.forClass(Disjunction.class)
      .with("alternatives", d -> d.alternatives.stream().map(Supplier::get)).exactClassOnly();

    private final Collection<Supplier<Simple>> alternatives;

    public Disjunction(Collection<Supplier<Simple>> alternatives) {
      Preconditions.checkArgument(alternatives.size() > 1, "alternatives must be >1: %s", alternatives);
      this.alternatives = alternatives.stream().map(EfFunctions::memoizing).collect(Collectors.toList());
    }

    @Nullable
    @Override
    public PCase minus(@Nonnull PCase type) {
      throw new UnsupportedOperationException(); // TODO
    }

    @Override
    public <T> T handle(PCaseHandler<? extends T> handler) {
      return handler.whenDisjunction(alternatives);
    }

    @Override
    public int hashCode() {
      return equality.hash(this);
    }
    
    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object other) {
      return equality.areEqual(this, other);
    }

    @Override
    public String toString() {
      return equality.toString(this);
    }
  }


  private static Function<TypedValue.StandardValue, Simple> subtract(final TypedValue.StandardValue lhs, final TypedValue rhs) {
    return standardRhs -> {
      Collection<Simple> lhsArgs = Lists.transform(lhs.args(), Simple::new);
      Collection<Simple> rhsArgs = Lists.transform(standardRhs.args(), Simple::new);
      assert lhsArgs.size() == rhsArgs.size() : String.format("lhs=%s, rhs=%s", lhs, rhs); // between an assert and a check, TODO add nArg validation
      List<TypedValue> resultArgs = Lists.newArrayListWithCapacity(lhsArgs.size());
      for (Map.Entry<Simple, Simple> pair : EfCollections.zip(lhsArgs, rhsArgs)) {
        PCase argResult = pair.getKey().minus(pair.getValue());
        if (argResult == null) {
          return null;
        }
        TypedValue resultValue = argResult.handle(new PCaseHandler<TypedValue>() {
          @Override
          public TypedValue whenAny() {
            throw new AssertionError(String.format("lhs=%s, rhs=%s", lhs, rhs));
          }

          @Override
          public TypedValue whenNone() {
            return null;
          }

          @Override
          public TypedValue whenSimple(TypedValue value) {
            return value;
          }

          @Override
          public TypedValue whenDisjunction(Collection<Supplier<Simple>> alternatives) {
            throw new AssertionError(String.format("lhs=%s, rhs=%s", lhs, rhs));
          }
        });
        resultArgs.add(resultValue);
      }
      if (resultArgs.stream().anyMatch(a -> a == null)) {
        throw new UnsupportedOperationException("What happens if only some args result in none? Return none? Failure?"); // TODO
      }
      return new Simple(new TypedValue.StandardValue(lhs.type(), resultArgs));
    };
  }
}

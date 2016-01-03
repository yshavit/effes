package com.yuvalshavit.effes.interpreter;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.effes.compile.node.EfVar;
import com.yuvalshavit.effes.compile.pmatch.PAlternative;
import com.yuvalshavit.util.EfCollections;
import com.yuvalshavit.util.EfFunctions;

public final class ExecutableCase {

  private final ExecutableExpression matchAgainst;
  private final List<CaseMatcher> caseMatchers;
  private final boolean isExpression;

  public ExecutableCase(ExecutableExpression matchAgainst, List<CaseMatcher> caseMatchers, boolean isExpression) {
    this.matchAgainst = matchAgainst;
    this.caseMatchers = ImmutableList.copyOf(caseMatchers);
    this.isExpression = isExpression;
  }

  public void execute(CallStack stack) {
    matchAgainst.execute(stack);
    EfValue peek = stack.peek();
    for (CaseMatcher matcher : caseMatchers) {
      if (matcher.matchAlternative.matches(peek)) {
        EfValue popped = stack.pop();// the type we matched against
        for (int i = 0; i < matcher.nBindings; ++i) {
          stack.push(null);
        }
        matcher.binder.accept(popped, stack);
        matcher.ifMatches.execute(stack);
        EfValue rv = isExpression
          ? stack.pop()
          : null;
        for (int i = 0; i < matcher.nBindings; ++i) {
          stack.pop();
        }
        if (isExpression) {
          stack.push(rv);
        }
        return;
      }
    }
    throw new IllegalStateException(String.format("no patterns matched (%s): %s", stack.pop(), toString()));
  }

  public static class CaseMatcher {
    private final PAlternative matchAlternative;
    private final ExecutableElement ifMatches;
    private final BiConsumer<EfValue,CallStack> binder;
    private final int nBindings;

    public CaseMatcher(PAlternative matchType, ExecutableElement ifMatches, Map<String,EfVar> bindings) {
      this.matchAlternative = matchType;
      this.ifMatches = ifMatches;
      binder = createBinder(matchType, bindings);
      nBindings = bindings.size();
    }

    private static BiConsumer<EfValue,CallStack> createBinder(PAlternative alternative, Map<String,EfVar> bindings) {
      return alternative.map(
        (type, args) -> buildSimpleTypeBinder(type, args, bindings),
        name -> buildWildcardBinder(name, bindings));
    }

    private static BiConsumer<EfValue,CallStack> buildWildcardBinder(String name, Map<String,EfVar> bindings) {
      if (name == null) {
        return EfFunctions.emptyBiConsumer();
      } else {
        EfVar efVar = bindings.get(name);
        checkNotNull(efVar, "efVar");
        return (value, stack) -> {
          stack.push(value);
          stack.popToLocal(efVar.getArgPosition());
        };
      }
    }

    private static BiConsumer<EfValue,CallStack> buildSimpleTypeBinder(EfType.SimpleType type, List<PAlternative> args, Map<String,EfVar> bindings) {
      List<BiConsumer<EfValue,CallStack>> recursions = new ArrayList<>(Lists.transform(args, arg -> createBinder(arg, bindings)));
      return (value, stack) -> {
        assert value.getType().getGeneric() == type.getGeneric() : String.format("%s != %s", value.getType().getGeneric(), type.getGeneric());
        assert value.getState().size() == args.size() : String.format("%s doesn't match %s", value.getState(), args);
        EfCollections.zipC(recursions, value.getState(), (binder, argValue) -> binder.accept(argValue, stack));
      };
    }

    @Override
    public String toString() {
      return String.format("of %sø", matchAlternative);
    }
  }
}

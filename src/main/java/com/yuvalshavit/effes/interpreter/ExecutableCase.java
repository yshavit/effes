package com.yuvalshavit.effes.interpreter;

import com.google.common.collect.ImmutableList;
import com.yuvalshavit.effes.compile.pmatch.PAlternative;

import java.util.List;

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
        // put its args on the stack, in reverse order
        List<EfValue> poppedState = popped.getState();
        poppedState.forEach(stack::push);
        matcher.ifMatches.execute(stack);
        EfValue rv = isExpression
          ? stack.pop()
          : null;
        poppedState.forEach(s -> stack.pop());
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

    public CaseMatcher(PAlternative matchType, ExecutableElement ifMatches) {
      this.matchAlternative = matchType;
      this.ifMatches = ifMatches;
    }

    @Override
    public String toString() {
      return String.format("of %sø", matchAlternative);
    }
  }
}

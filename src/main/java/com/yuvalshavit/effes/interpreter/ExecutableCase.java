package com.yuvalshavit.effes.interpreter;

import com.google.common.collect.ImmutableList;
import com.yuvalshavit.effes.compile.node.EfType;

import java.util.List;

public final class ExecutableCase {

  private final ExecutableExpression matchAgainst;
  private final List<CaseMatcher> caseMatchers;

  public ExecutableCase(ExecutableExpression matchAgainst, List<CaseMatcher> caseMatchers) {
    this.matchAgainst = matchAgainst;
    this.caseMatchers = ImmutableList.copyOf(caseMatchers);
  }

  public void execute(CallStack stack) {
    matchAgainst.execute(stack);
    EfValue peek = stack.peek();
    for (CaseMatcher matcher : caseMatchers) {
      if (matcher.matchType.equals(peek.getType())) {
        EfValue popped = stack.pop();// the type we matched against
        // put its args on the stack, in reverse order
        List<EfValue> poppedState = popped.getState();
        poppedState.forEach(stack::push);
        matcher.ifMatches.execute(stack);
        poppedState.forEach(s -> stack.pop());
        return;
      }
    }
    throw new AssertionError(String.format("no patterns matched (%s): %s", stack.pop(), toString()));
  }

  public static class CaseMatcher {
    private final EfType.SimpleType matchType;
    private final ExecutableElement ifMatches;

    public CaseMatcher(EfType.SimpleType matchType, ExecutableElement ifMatches) {
      this.matchType = matchType;
      this.ifMatches = ifMatches;
    }

    @Override
    public String toString() {
      return String.format("of %s", matchType);
    }
  }
}

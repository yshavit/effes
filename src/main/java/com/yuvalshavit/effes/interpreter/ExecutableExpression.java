package com.yuvalshavit.effes.interpreter;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.yuvalshavit.effes.compile.EfType;
import com.yuvalshavit.effes.compile.Expression;

import java.util.List;
import java.util.function.Supplier;

public abstract class ExecutableExpression implements ExecutableElement {
  private final Expression source;

  private ExecutableExpression(Expression source) {
    this.source = source;
  }

  @Override
  public String toString() {
    return source.toString();
  }

  public static class CaseExpression extends ExecutableExpression {
    private final ExecutableExpression matchAgainst;
    private final List<CaseMatcher> caseMatchers;

    public CaseExpression(Expression source, ExecutableExpression matchAgainst, List<CaseMatcher> caseMatchers) {
      super(source);
      this.matchAgainst = matchAgainst;
      this.caseMatchers = ImmutableList.copyOf(caseMatchers);
    }

    @Override
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
      private final ExecutableExpression ifMatches;

      public CaseMatcher(EfType.SimpleType matchType, ExecutableExpression ifMatches) {
        this.matchType = matchType;
        this.ifMatches = ifMatches;
      }

      @Override
      public String toString() {
        return String.format("of %s", matchType);
      }
    }
  }

  public static class CtorExpression extends ExecutableExpression {
    private final EfType.SimpleType ctorType;
    private final List<ExecutableExpression> args;

    public CtorExpression(Expression.CtorInvoke source, List<ExecutableExpression> args) {
      super(source);
      ctorType = source.simpleType();
      this.args = ImmutableList.copyOf(Lists.reverse(args)); // CallStack expects them in reverse order!
    }

    @Override
    public void execute(CallStack stack) {
      args.forEach(a -> a.execute(stack));
      stack.push(ctorType);
    }
  }

  public static class MethodInvokeExpression extends ExecutableExpression {
    private final List<ExecutableExpression> args;
    private final Supplier<ExecutableMethod> body;
    private final boolean hasRv;

    public MethodInvokeExpression(Expression.MethodInvoke source,
                                  List<ExecutableExpression> args,
                                  Supplier<ExecutableMethod> body) {
      super(source);
      this.args = args;
      this.body = body;
      this.hasRv = !EfType.VOID.equals(source.resultType());
    }

    @Override
    public void execute(CallStack stack) {
      invoke(body.get(), args, stack, hasRv);
    }

    public boolean hasRv() {
      return hasRv;
    }

    public static void invoke(ExecutableMethod body, List<ExecutableExpression> args, CallStack stack, boolean hasRv) {
      stack.openFrame(args, hasRv);
      for (int nVars = body.nVars(); nVars > 0; --nVars) {
        stack.push((EfValue)null);
      }
      body.execute(stack);
      stack.closeFrame();
    }
  }

  public static class VarReadExpression extends ExecutableExpression {
    private final int pos;
    private final boolean isArg;

    public VarReadExpression(Expression.VarExpression source) {
      super(source);
      this.pos = source.pos();
      this.isArg = source.isArg();
    }

    @Override
    public void execute(CallStack stack) {
      if (isArg) {
        stack.pushArgToStack(pos);
      } else {
        stack.pushLocalToStack(pos);
      }
    }
  }
}

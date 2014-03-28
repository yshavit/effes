package com.yuvalshavit.effes.interpreter;

import com.google.common.collect.ImmutableList;
import com.yuvalshavit.effes.compile.EfType;
import com.yuvalshavit.effes.compile.Expression;
import com.yuvalshavit.effes.compile.StatementCompiler;

import java.util.List;
import java.util.function.Predicate;

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
      for (CaseMatcher matcher : caseMatchers) {
        if (matcher.match.test(stack)) {
          stack.pop(); // the type we matched against
          matcher.ifMatches.execute(stack);
          return;
        }
      }
      throw new AssertionError(String.format("no patterns matched (%s): %s", stack.pop(), toString()));
    }

    public static class CaseMatcher {
      private final PatternMatch match;
      private final ExecutableExpression ifMatches;

      public CaseMatcher(PatternMatch match, ExecutableExpression ifMatches) {
        this.match = match;
        this.ifMatches = ifMatches;
      }
    }
  }

  public static class CtorExpression extends ExecutableExpression {
    private final EfType.SimpleType ctorType;

    public CtorExpression(Expression.CtorInvoke source) {
      super(source);
      ctorType = source.simpleType();
    }

    @Override
    public void execute(CallStack stack) {
      stack.push(ctorType);
    }
  }

  public static class MethodInvokeExpression extends ExecutableExpression {
    private final List<ExecutableExpression> args;
    private final ExecutableElement body;

    public MethodInvokeExpression(Expression.MethodInvoke source, List<ExecutableExpression> args, ExecutableElement body) {
      super(source);
      this.args = args;
      this.body = body;
    }

    @Override
    public void execute(CallStack stack) {
      invoke(body, args, stack);
    }

    public static void invoke(ExecutableElement body, List<ExecutableExpression> args, CallStack stack) {
      stack.openFrame(args);
      boolean pushLocal = StatementCompiler.sawOneAssignment.get();
      if (pushLocal) {
        stack.push(null); // var placeholder
      }
      body.execute(stack);
      stack.closeFrame();
    }
  }

  public static class ArgReadExpression extends ExecutableExpression {
    private final int pos;
    public ArgReadExpression(Expression.ArgExpression source) {
      super(source);
      this.pos = source.pos();
    }

    @Override
    public void execute(CallStack stack) {
      stack.pushArgToStack(pos);
    }
  }

  public static class VarReadExpression extends ExecutableExpression {
    private final int pos;

    public VarReadExpression(Expression.VarExpression source) {
      super(source);
      this.pos = source.pos();
    }

    @Override
    public void execute(CallStack stack) {
      stack.pushLocalToStack(pos);
    }
  }

  public interface PatternMatch extends Predicate<CallStack> {}
}

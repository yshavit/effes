package com.yuvalshavit.effes.interpreter;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.effes.compile.node.EfVar;
import com.yuvalshavit.effes.compile.node.Expression;

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
    private final ExecutableCase delegate;

    public CaseExpression(Expression source, ExecutableExpression matchAgainst, List<ExecutableCase.CaseMatcher> caseMatchers) {
      super(source);
      this.delegate = new ExecutableCase(matchAgainst, caseMatchers);
    }

    @Override
    public void execute(CallStack stack) {
      delegate.execute(stack);
    }
  }

  public static class AssignExpression extends ExecutableExpression {
    private final ExecutableExpression delegate;
    private final EfVar assignTo;

    public AssignExpression(Expression.AssignExpression source, ExecutableExpression delegate) {
      super(source);
      this.delegate = delegate;
      this.assignTo = source.getVar();
    }

    @Override
    public void execute(CallStack stack) {
      delegate.execute(stack);
      stack.push(stack.peek());
      stack.popToLocal(assignTo.getArgPosition());
    }
  }

  public static class CastExpression extends ExecutableExpression {
    private final ExecutableExpression delegate;
    private final EfType castTo;

    public CastExpression(Expression.CastExpression source, ExecutableExpression delegate) {
      super(source);
      this.delegate = delegate;
      this.castTo = source.resultType();
    }

    @Override
    public void execute(CallStack stack) {
      delegate.execute(stack);
      EfValue stackTop = stack.peek();
      EfType.SimpleType actualType = stackTop.getType();
      if (!castTo.contains(actualType)) {
        throw new ClassCastException("expected type " + castTo + " but found " + stackTop);
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
      stack.push(ctorType, args.size());
    }
  }

  public static class InstanceArg extends ExecutableExpression {
    private final ExecutableExpression target;
    private final EfVar arg;

    public InstanceArg(Expression source, ExecutableExpression target, EfVar arg) {
      super(source);
      this.target = target;
      this.arg = arg;
    }

    @Override
    public void execute(CallStack stack) {
      target.execute(stack);
      EfValue targetValue = stack.pop();
      EfValue argValue = targetValue.getState().get(arg.getArgPosition());
      stack.push(argValue);
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

    public static void invoke(ExecutableMethod body, List<ExecutableExpression> args, CallStack stack, boolean hasRv) {
      stack.openFrame(args, hasRv);
      for (int nVars = body.nVars(); nVars > 0; --nVars) {
        stack.push(null);
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

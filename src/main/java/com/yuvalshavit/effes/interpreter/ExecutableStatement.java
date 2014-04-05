package com.yuvalshavit.effes.interpreter;

import com.yuvalshavit.effes.compile.Expression;
import com.yuvalshavit.effes.compile.Statement;

import java.util.List;

public abstract class ExecutableStatement implements ExecutableElement {
  private final Statement source;

  protected ExecutableStatement(Statement source) {
    this.source = source;
  }

  @Override
  public String toString() {
    return source.toString();
  }

  public static class AssignStatement extends ExecutableStatement {
    private final int pos;
    private final ExecutableExpression value;

    public AssignStatement(Statement.AssignStatement source, ExecutableExpressionCompiler expressionCompiler) {
      super(source);
      this.pos = source.var().getArgPosition();
      this.value = expressionCompiler.apply(source.value());
    }

    @Override
    public void execute(CallStack stack) {
      value.execute(stack);
      stack.popToLocal(pos);
    }
  }

  public static class CaseStatement extends ExecutableStatement {
    private final ExecutableCase delegate;

    public CaseStatement(Statement source, ExecutableExpression matchAgainst, List<ExecutableCase.CaseMatcher> caseMatchers) {
      super(source);
      this.delegate = new ExecutableCase(matchAgainst, caseMatchers);
    }

    @Override
    public void execute(CallStack stack) {
      delegate.execute(stack);
    }
  }

  public static class ReturnStatement extends ExecutableStatement {
    private final ExecutableExpression value;

    public ReturnStatement(Statement.ReturnStatement source, ExecutableExpressionCompiler expressionCompiler) {
      super(source);
      this.value = expressionCompiler.apply(source.getExpression());
    }

    @Override
    public void execute(CallStack stack) {
      value.execute(stack);
      stack.popToRv();
    }
  }

  public static class MethodInvoke extends ExecutableStatement {
    private final ExecutableExpression.MethodInvokeExpression method;

    public MethodInvoke(Statement.MethodInvoke source,
                        ExecutableExpressionCompiler expressionCompiler,
                        Expression.MethodInvoke method)
    {
      super(source);
      this.method = expressionCompiler.methodInvoke(method);
    }

    @Override
    public void execute(CallStack stack) {
      method.execute(stack);
      if (method.hasRv()) {
        stack.pop(); // get rid of the return value
      }
    }
  }
}

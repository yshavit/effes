package com.yuvalshavit.effes.interpreter;

import com.yuvalshavit.effes.compile.node.Expression;
import com.yuvalshavit.effes.compile.node.Statement;

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
      this.delegate = new ExecutableCase(matchAgainst, caseMatchers, false);
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
    private final ExecutableExpression method;

    public MethodInvoke(Statement.MethodInvoke source,
                        ExecutableExpressionCompiler expressionCompiler,
                        Expression method)
    {
      super(source);
      this.method = expressionCompiler.apply(method);
    }

    @Override
    public void execute(CallStack stack) {
      int initialDepth = stack.depth();
      method.execute(stack);
      int nextDepth = stack.depth();
      if (nextDepth != initialDepth) {
        assert nextDepth == initialDepth + 1 : String.format("stack depth, expected %d or %d but saw %d",
                                                             initialDepth,
                                                             initialDepth + 1,
                                                             nextDepth);
        stack.pop(); // get rid of the return value
      }
    }
  }
}

package com.yuvalshavit.effes.interpreter;

import com.google.common.collect.ImmutableList;
import com.yuvalshavit.effes.compile.node.Block;
import com.yuvalshavit.effes.compile.node.Statement;
import com.yuvalshavit.util.Dispatcher;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ExecutableStatementCompiler implements Function<Statement, ExecutableStatement> {

  private final ExecutableExpressionCompiler expressionCompiler;

  public ExecutableStatementCompiler(ExecutableExpressionCompiler expressionCompiler)
  {
    this.expressionCompiler = expressionCompiler;
  }

  @Override
  public ExecutableStatement apply(Statement statement) {
    return dispatcher.apply(this, statement);
  }

  private static final Dispatcher<ExecutableStatementCompiler, Statement, ExecutableStatement> dispatcher =
    Dispatcher.builder(ExecutableStatementCompiler.class, Statement.class, ExecutableStatement.class)
      .put(Statement.AssignStatement.class, ExecutableStatementCompiler::assignStatement)
      .put(Statement.MethodInvoke.class, ExecutableStatementCompiler::methodInvoke)
      .put(Statement.ReturnStatement.class, ExecutableStatementCompiler::returnStat)
      .put(Statement.CaseStatement.class, ExecutableStatementCompiler::caseStat)
      .put(Statement.UnrecognizedStatement.class, (me, s) -> {
        throw new AssertionError(s);
      })
      .build((c, s) -> {
        throw new AssertionError(s);
      });

  private ExecutableStatement assignStatement(Statement.AssignStatement stat) {
    return new ExecutableStatement.AssignStatement(stat, expressionCompiler);
  }

  private ExecutableStatement caseStat(Statement.CaseStatement stat) {
    ExecutableExpression matchAgainst = expressionCompiler.apply(stat.construct().getMatchAgainst());
    List<ExecutableCase.CaseMatcher> matchers = stat.construct().getPatterns().stream().map(p -> {
      ExecutableElement ifMatch = block(p.getIfMatched());
      return new ExecutableCase.CaseMatcher(p.getPAlternative(), ifMatch);
    }).collect(Collectors.toList());
    return new ExecutableStatement.CaseStatement(stat, matchAgainst, matchers);
  }

  private ExecutableStatement methodInvoke(Statement.MethodInvoke stat) {
    return new ExecutableStatement.MethodInvoke(stat, expressionCompiler, stat.methodExpr());
  }

  private ExecutableStatement returnStat(Statement.ReturnStatement returnStatement) {
    return new ExecutableStatement.ReturnStatement(returnStatement, expressionCompiler);
  }

  private ExecutableElement block(Block block) {
    List<ExecutableStatement> stats = ImmutableList.copyOf(
      block.statements().stream().map(this::apply).collect(Collectors.toList()));
    return stack -> stats.forEach(s -> s.execute(stack));
  }
}

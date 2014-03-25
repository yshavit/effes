package com.yuvalshavit.effes.interpreter;

import com.google.common.collect.Lists;
import com.yuvalshavit.effes.compile.Block;
import com.yuvalshavit.effes.compile.EfMethod;
import com.yuvalshavit.effes.compile.Expression;
import com.yuvalshavit.effes.compile.MethodsRegistry;
import com.yuvalshavit.effes.compile.SimpleType;
import com.yuvalshavit.effes.compile.Statement;
import com.yuvalshavit.effes.TUtils;
import com.yuvalshavit.effes.compile.TypeRegistry;
import com.yuvalshavit.util.Dispatcher;
import com.yuvalshavit.util.DispatcherTestBase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;

import static org.testng.Assert.assertEquals;

public final class ExecutableStatementCompilerTest extends DispatcherTestBase {

  @DataProvider(name = DispatcherTestBase.providerName)
  public static Object[][] statementSubclasses() {
    return DispatcherTestBase.findSubclasses(Statement.class);
  }

  @Override
  protected Dispatcher<?, ?, ?> getDispatcherUnderTest() {
    return ExecutableStatementCompiler.StatementCompiler.dispatcher;
  }

  @Test
  public void returnStat() {
    TypeRegistry typeRegistry = TUtils.typeRegistry("True");
    Expression trueCtor = new Expression.CtorInvoke(TUtils.getExistingType(typeRegistry, "True"));
    Statement returnStat = new Statement.ReturnStatement(trueCtor);
    ExecutableExpressionCompiler expressionCompiler = new ExecutableExpressionCompiler();
    MethodsRegistry<Block> methods = new MethodsRegistry<>();
    ExecutableStatementCompiler statementCompiler = new ExecutableStatementCompiler(expressionCompiler, methods);
    ExecutableStatement executable = statementCompiler.apply(returnStat);
    StateStack states = new StateStack();
    executable.execute(states);
    assertEquals(states.getAllStates(), Lists.newArrayList(typeRegistry.getSimpleType("True")));
  }

  @Test
  public void methodInvoke() {
    TypeRegistry typeRegistry = TUtils.typeRegistry("True", "False");
    SimpleType trueType = TUtils.getExistingType(typeRegistry, "True");
    SimpleType falseType = TUtils.getExistingType(typeRegistry, "False");
    MethodsRegistry<Block> methods = new MethodsRegistry<>();

    Expression falseCtor = new Expression.CtorInvoke(TUtils.getExistingType(typeRegistry, "False"));
    Statement returnFalse = new Statement.ReturnStatement(falseCtor);
    Block body = new Block(Arrays.asList(returnFalse));
    methods.registerTopLevelMethod("not", new EfMethod<>(Arrays.asList(trueType), falseType, body));

    ExecutableExpressionCompiler expressionCompiler = new ExecutableExpressionCompiler();
    ExecutableStatementCompiler statementCompiler = new ExecutableStatementCompiler(expressionCompiler, methods);
    Expression trueCtor = new Expression.CtorInvoke(TUtils.getExistingType(typeRegistry, "True"));
    Statement methodInvoke = new Statement.MethodInvoke("not", Arrays.asList(trueCtor));
    ExecutableStatement executable = statementCompiler.apply(methodInvoke);

    StateStack states = new StateStack();
    executable.execute(states);
    assertEquals(states.getAllStates(), Lists.newArrayList(typeRegistry.getSimpleType("False")));
  }
}

package com.yuvalshavit.effes.interpreter;

public final class ExecutableStatementCompilerTest {

//  @Test
//  public void returnStat() {
//    TypeRegistry typeRegistry = TUtils.typeRegistry("True");
//    Expression trueCtor = new Expression.CtorInvoke(TUtils.getExistingType(typeRegistry, "True"));
//    Statement returnStat = new Statement.ReturnStatement(trueCtor);
//    ExecutableExpressionCompiler expressionCompiler = new ExecutableExpressionCompiler();
//    MethodsRegistry<Block> methods = new MethodsRegistry<>();
//    ExecutableStatementCompiler statementCompiler = new ExecutableStatementCompiler(expressionCompiler, methods);
//    ExecutableStatement executable = statementCompiler.apply(returnStat);
//    StateStack states = new StateStack();
//    executable.execute(states);
//    assertEquals(states.getAllStates(), Lists.newArrayList(typeRegistry.getSimpleType("True")));
//  }
//
//  @Test
//  public void methodInvoke() {
//    TypeRegistry typeRegistry = TUtils.typeRegistry("True", "False");
//    SimpleType trueType = TUtils.getExistingType(typeRegistry, "True");
//    SimpleType falseType = TUtils.getExistingType(typeRegistry, "False");
//    MethodsRegistry<Block> methods = new MethodsRegistry<>();
//
//    Expression falseCtor = new Expression.CtorInvoke(TUtils.getExistingType(typeRegistry, "False"));
//    Statement returnFalse = new Statement.ReturnStatement(falseCtor);
//    Block body = new Block(Arrays.asList(returnFalse));
//    methods.registerTopLevelMethod("not", new EfMethod<>(Arrays.asList(trueType), falseType, body));
//    methods.transform()
//
//    ExecutableExpressionCompiler expressionCompiler = new ExecutableExpressionCompiler();
//    MethodsRegistry<ExecutableBlock> executableMethods = new MethodsRegistry<>();
//    ExecutableStatementCompiler statementCompiler = new ExecutableStatementCompiler(expressionCompiler, executableMethods);
//    Expression trueCtor = new Expression.CtorInvoke(TUtils.getExistingType(typeRegistry, "True"));
//    Statement methodInvoke = new Statement.MethodInvoke("not", Arrays.asList(trueCtor));
//    ExecutableStatement executable = statementCompiler.apply(methodInvoke);
//
//    StateStack states = new StateStack();
//    executable.execute(states);
//    assertEquals(states.getAllStates(), Lists.newArrayList(typeRegistry.getSimpleType("False")));
//  }
}

package com.yuvalshavit.effes.interpreter;

import com.google.common.collect.ImmutableList;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.effes.compile.node.EfVar;
import org.testng.annotations.Test;

import java.util.NoSuchElementException;
import java.util.function.Consumer;

import static com.yuvalshavit.util.AssertException.assertException;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public final class CallStackTest {

  @Test
  public void methodInvokeNoArgs() {
    CallStack stack = new CallStack();
    Object initial = stack.snapshot();
    open(stack);
    push(stack, "Foo");
    close(stack);
    assertEquals(pop(stack), "Foo");
    assertEquals(stack.snapshot(), initial);
  }

  @Test
  public void methodInvokeOneArg() {
    CallStack stack = new CallStack();
    Object initial = stack.snapshot();
    open(stack, pushExpr("test-a0"));
    assertEquals(peekArg(stack, 0), "test-a0");

    push(stack, "Foo");

    close(stack);
    assertEquals(pop(stack), "Foo");
    assertEquals(stack.snapshot(), initial);
  }

  private static ExecutableElement pushExpr(String arg) {
    return s -> push(s, arg);
  }

  @Test
  public void methodInvokeThreeArgs() {
    CallStack stack = new CallStack();
    Object initial = stack.snapshot();
    open(stack, pushExpr("test-a0"), pushExpr("test-a1"), pushExpr("test-a2"));
    assertEquals(peekArg(stack, 0), "test-a0");
    assertEquals(peekArg(stack, 1), "test-a1");
    assertEquals(peekArg(stack, 2), "test-a2");

    push(stack, "Foo");
    close(stack);
    assertEquals(pop(stack), "Foo");
    assertEquals(stack.snapshot(), initial);
  }

  @Test
  public void peekArg() {
    CallStack stack = new CallStack();
    open(stack, pushExpr("test-a0"), pushExpr("test-a1"), pushExpr("test-a2"), pushExpr("test-a3"));

    Object snapshot = stack.snapshot();
    assertEquals(peekArg(stack, 2), "test-a2");
    assertEquals(stack.snapshot(), snapshot);
  }

  @Test
  public void pushArgToStack() {
    CallStack stack = new CallStack();
    open(stack, pushExpr("test-a0"), pushExpr("test-a1"), pushExpr("test-a2"), pushExpr("test-a3"));

    Object snapshot = stack.snapshot();
    assertException(NoSuchElementException.class, stack::peek);
    assertEquals(stack.snapshot(), snapshot);
    stack.pushArgToStack(2);
    assertEquals(pop(stack), "test-a2");
    assertEquals(stack.snapshot(), snapshot);
  }

  @Test
  public void peekArgBounds() {
    CallStack stack = new CallStack();
    open(stack, pushExpr("test-a0"), pushExpr("test-a1"));

    Object initial = stack.snapshot();

    assertException(IndexOutOfBoundsException.class, () -> peekArg(stack, -1));
    assertEquals(stack.snapshot(), initial);

    assertEquals("test-a0", peekArg(stack, 0));
    assertEquals(stack.snapshot(), initial);

    assertEquals("test-a1", peekArg(stack, 1));
    assertEquals(stack.snapshot(), initial);

    assertException(IndexOutOfBoundsException.class, () -> peekArg(stack, 2));

    assertEquals(stack.snapshot(), initial);
  }

  @Test(enabled = false)
  public void pushArgToStackBounds() {
    CallStack stack = new CallStack();
    open(stack, pushExpr("test-a0"), pushExpr("test-a1"));

    Object initial = stack.snapshot();

    stack.pushArgToStack(-1); // undefined, but doesn't throw
    assertEquals(stack.snapshot(), initial);

    stack.pushArgToStack(0);
    assertEquals("test-a0", pop(stack));
    assertEquals(stack.snapshot(), initial);

    stack.pushArgToStack(1);
    assertEquals("test-a1", pop(stack));
    assertEquals(stack.snapshot(), initial);

    stack.pushArgToStack(2); // undefined, but doesn't throw
    assertEquals(stack.snapshot(), initial);
  }

  @Test
  public void pushPeekPop() {
    CallStack stack = new CallStack();
    Object initial = stack.snapshot();

    push(stack, "p0");
    assertEquals(peek(stack), "p0");

    push(stack, "p1");
    assertEquals(peek(stack), "p1");

    push(stack, "p2");
    assertEquals(peek(stack), "p2");

    assertEquals(pop(stack), "p2");

    assertEquals(pop(stack), "p1");

    assertEquals(pop(stack), "p0");
    assertEquals(stack.snapshot(), initial);

    assertException(IllegalStateException.class, stack::pop);
  }

  @Test
  public void popPastFrame() {
    CallStack stack = new CallStack();
    open(stack);

    assertException(IllegalStateException.class, stack::pop);
  }

  @Test
  public void nestedMethods() {
    CallStack stack = new CallStack();

    // call method a
    Object beforeA = stack.snapshot();
    open(stack, pushExpr("a0"), pushExpr("a1"));

    // call method b
    Object beforeB = stack.snapshot();
    open(stack, pushExpr("b0"), pushExpr("b1"), pushExpr("b2"));

    // return "rv-b" from method b
    push(stack, "rv-b");
    close(stack);
    assertEquals(pop(stack), "rv-b");
    assertEquals(stack.snapshot(), beforeB);

    // return "rv-a" from method a
    push(stack, "rv-a");
    close(stack);
    assertEquals(pop(stack), "rv-a");
    assertEquals(stack.snapshot(), beforeA);
  }

  @Test
  public void argAlsoInvokes() {
    CallStack stack = new CallStack();
    Object initial = stack.snapshot();

    ExecutableElement invokingArg = s -> {
      open(stack, pushExpr("innerMethodArg"));
      push(stack, "innerMethodRv");
      close(stack);
    };

    open(stack, pushExpr("outerMethodA0"), invokingArg);
    assertEquals(peekArg(stack, 0), "outerMethodA0");
    assertEquals(peekArg(stack, 1), "innerMethodRv");
    push(stack, "outerMethodRv");
    close(stack);
    assertEquals("outerMethodRv", pop(stack));
    assertEquals(stack.snapshot(), initial);
  }

  @Test
  public void closeWithoutOpening() {
    CallStack stack = new CallStack();
    assertException(IllegalStateException.class, stack::closeFrame);
  }

  @Test
  public void pushLocalToStackWhenFresh() {
    CallStack stack = new CallStack();
    push(stack, "Foo");
    push(stack, "Bar");
    stack.pushLocalToStack(0);
    assertEquals(pop(stack), "Foo");
    assertEquals(pop(stack), "Bar");
    assertEquals(pop(stack), "Foo");
  }

  @Test
  public void pushLocalToStackWithinFrame() {
    CallStack stack = new CallStack();
    Object initial = stack.snapshot();
    open(stack, pushExpr("a0"), pushExpr("a1"));

    push(stack, "Foo");
    push(stack, "Bar");
    stack.pushLocalToStack(0);
    assertEquals(pop(stack), "Foo"); // from pushLocalToStack
    assertEquals(pop(stack), "Bar");

    close(stack);
    assertEquals(pop(stack), "Foo"); // the "original" one
    assertEquals(stack.snapshot(), initial);
  }

  @Test
  public void pushLocalToStackBounds() {
    Consumer<Consumer<CallStack>> setupAndRun = action -> {
      CallStack stack = new CallStack();
      push(stack, "One");
      push(stack, "Two");
      push(stack, "Three");
      action.accept(stack);
    };

    setupAndRun.accept(s -> assertException(IndexOutOfBoundsException.class, () -> s.pushLocalToStack(-1)));
    setupAndRun.accept(s -> {
      s.pushLocalToStack(0);
      assertEquals(pop(s), "One");
    });
    setupAndRun.accept(s -> {
      s.pushLocalToStack(1);
      assertEquals(pop(s), "Two");
    });
    setupAndRun.accept(s -> {
      s.pushLocalToStack(2);
      assertEquals(pop(s), "Three");
    });
    setupAndRun.accept(s -> assertException(IndexOutOfBoundsException.class, () -> s.pushLocalToStack(3)));
  }

  @Test
  public void popToLocalWhenFresh() {
    CallStack stack = new CallStack();
    Object initial = stack.snapshot();
    push(stack, "one");
    push(stack, "two");
    push(stack, "three");
    stack.popToLocal(0);
    assertEquals(pop(stack), "two");
    assertEquals(pop(stack), "three"); // from the write
    assertEquals(stack.snapshot(), initial);
  }

  @Test
  public void popToLocalWithinFrame() {
    CallStack stack = new CallStack();
    Object initial = stack.snapshot();
    open(stack, pushExpr("a0"), pushExpr("a1"));

    push(stack, "one");
    push(stack, "two");
    push(stack, "three");
    stack.popToLocal(0);
    assertEquals(pop(stack), "two");

    close(stack);
    assertEquals(pop(stack), "three"); // from the write, then rv
    assertEquals(stack.snapshot(), initial);
  }

  @Test
  public void popToLocalBounds() {
    final Object initial;
    {
      CallStack stack = new CallStack();
      initial = stack.snapshot();
    }
    Consumer<Consumer<CallStack>> setupAndRun = action -> {
      CallStack stack = new CallStack();
      push(stack, "One");
      push(stack, "Two");
      push(stack, "Three");
      action.accept(stack);
    };

    setupAndRun.accept(s -> assertException(IndexOutOfBoundsException.class, () -> s.popToLocal(-1)));
    setupAndRun.accept(s -> {
      s.popToLocal(0);
      assertEquals(pop(s), "Two");
      assertEquals(pop(s), "Three");
      assertEquals(s.snapshot(), initial);
    });
    setupAndRun.accept(s -> {
      s.popToLocal(1);
      assertEquals(pop(s), "Three");
      assertEquals(pop(s), "One");
      assertEquals(s.snapshot(), initial);
    });
    setupAndRun.accept(s -> assertException(IndexOutOfBoundsException.class, () -> s.popToLocal(2)));
    setupAndRun.accept(s -> assertException(IndexOutOfBoundsException.class, () -> s.popToLocal(3)));
  }

  @Test
  public void methodInvokeWithLocalVars() {
    CallStack stack = new CallStack();
    Object initial = stack.snapshot();
    open(stack, pushExpr("a0"), pushExpr("a1"), pushExpr("a2"));
    push(stack, "local-0");
    push(stack, "rv-obj");
    close(stack);
    assertEquals(pop(stack), "rv-obj");
    assertEquals(stack.snapshot(), initial);
  }

  @Test
  public void localVarInOuterMethod() {
    // We'll work with two methods, outer and inner. This is analogous to outer() { inner(); }
    // Outer has one local var.
    CallStack stack = new CallStack();
    Object initial = stack.snapshot();

    // open outer
    open(stack);
    push(stack, "wrapper-local");

    // open inner, and then instantly return from it and pop off its return value
    Object outerState = stack.snapshot();
    open(stack);
    push(stack, "inner-rv");
    close(stack);
    assertEquals(pop(stack), "inner-rv");
    // make sure the stack is as we left it
    assertEquals(stack.snapshot(), outerState);

    // and finally, return from outer
    push(stack, "wrapping-rv");
    close(stack);
    assertEquals(pop(stack), "wrapping-rv");
    assertEquals(stack.snapshot(), initial);
  }

  @Test
  public void simpleCtorWithArgs() {
    EfType.SimpleType argedType = new EfType.SimpleType("ArgsTest");
    argedType.setCtorArgs(ImmutableList.of(
      EfVar.arg("first", 0, stringType),
      EfVar.arg("second", 1, stringType)
    ));

    CallStack stack = new CallStack();
    Object snapshot = stack.snapshot();

    push(stack, "second value");
    push(stack, "first value");
    stack.push(argedType);

    EfValue value = stack.pop();
    assertEquals(value.getType(), argedType);
    assertEquals(value.getState(), ImmutableList.of(
      EfValue.of(stringType, "first value"),
      EfValue.of(stringType, "second value")));

    assertEquals(stack.snapshot(), snapshot);
  }

  @Test
  public void noRv() {
    CallStack stack = new CallStack();
    Object snapshot = stack.snapshot();
    stack.openFrame(ImmutableList.of(), false);
    stack.closeFrame();
    assertEquals(stack.snapshot(), snapshot);
  }

  @Test
  public void noRvButPoppedToRv() {
    CallStack stack = new CallStack();
    Object snapshot = stack.snapshot();
    stack.openFrame(ImmutableList.of(), false);
    push(stack, "not a return value");
    assertException(IllegalStateException.class, stack::popToRv);
    stack.closeFrame();
    assertEquals(stack.snapshot(), snapshot);
  }

  @Test
  public void hasRvIsSet() {
    // methods A, B, and C, nested (A is outermost). B doesn't have an RV.
    CallStack stack = new CallStack();

    // call A
    stack.openFrame(ImmutableList.of(), true);
    assertFalse(stack.rvIsSet());

    // call B
    stack.openFrame(ImmutableList.of(), false);
    assertFalse(stack.rvIsSet());

    // call C
    stack.openFrame(ImmutableList.of(), true);
    assertFalse(stack.rvIsSet());

    // return from C
    push(stack, "C rv");
    stack.popToRv();
    assertTrue(stack.rvIsSet());
    stack.closeFrame();

    // we're now back in B
    stack.pop(); // C's rv
    assertFalse(stack.rvIsSet());

    // return from B
    stack.closeFrame();

    // we're now back in A
    assertFalse(stack.rvIsSet());

    // return from A
    push(stack, "A rv");
    stack.popToRv();
    assertTrue(stack.rvIsSet());
    stack.closeFrame();
  }

  private static void open(CallStack stack, ExecutableElement... args) {
    stack.openFrame(ImmutableList.copyOf(args), true);
  }

  private static void close(CallStack stack) {
    stack.popToRv();
    stack.closeFrame();
  }
  
  private static void push(CallStack stack, String value) {
    stack.push(EfValue.of(stringType, value));
  }

  private static String pop(CallStack stack) {
    EfValue pop = stack.pop();
    return ((EfValue.EfStringValue)pop).getString();
  }

  private static String peekArg(CallStack stack, int pos) {
    EfValue peek = stack.peekArg(pos);
    return ((EfValue.EfStringValue)peek).getString();
  }

  private static String peek(CallStack stack) {
    EfValue peek = stack.peek();
    return ((EfValue.EfStringValue)peek).getString();
  }

  private static final EfType.SimpleType stringType = new EfType.SimpleType("Tt");
}

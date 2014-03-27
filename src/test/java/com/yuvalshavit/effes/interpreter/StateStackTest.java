package com.yuvalshavit.effes.interpreter;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.Test;

import static com.yuvalshavit.util.AssertException.assertException;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

public final class StateStackTest {

  @Test
  public void methodInvokeNoArgs() {
    StateStack stack = new StateStack();
    final int initialDepth = stack.depth();
    open(stack);
    assertEquals(stack.currentFrameArgsCount(), 0);
    stack.push("Foo");
    assertEquals(stack.currentFrameArgsCount(), 0);
    stack.closeFrame();
    assertEquals(stack.depth(), initialDepth + 1);
    assertEquals(stack.peek(), "Foo");
  }

  @Test
  public void methodInvokeOneArg() {
    StateStack stack = new StateStack();
    final int initialDepth = stack.depth();
    open(stack, pushExpr("test-a0"));
    assertEquals(stack.peekArg(0), "test-a0");

    assertEquals(stack.currentFrameArgsCount(), 1);
    stack.push("Foo");
    assertEquals(stack.currentFrameArgsCount(), 1);

    stack.closeFrame();
    assertEquals(stack.depth(), initialDepth + 1);
    assertEquals(stack.peek(), "Foo");
  }

  private ExecutableElement pushExpr(String arg) {
    return s -> s.push(arg);
  }

  @Test
  public void methodInvokeThreeArgs() {
    StateStack stack = new StateStack();
    final int initialDepth = stack.depth();
    open(stack, pushExpr("test-a0"), pushExpr("test-a1"), pushExpr("test-a2"));
    assertEquals(stack.peekArg(0), "test-a0");
    assertEquals(stack.peekArg(1), "test-a1");
    assertEquals(stack.peekArg(2), "test-a2");

    assertEquals(stack.currentFrameArgsCount(), 3);
    stack.push("Foo");
    assertEquals(stack.currentFrameArgsCount(), 3);
    stack.closeFrame();
    assertEquals(stack.depth(), initialDepth + 1);
    assertEquals(stack.peek(), "Foo");
  }

  @Test
  public void peekArg() {
    StateStack stack = new StateStack();
    open(stack, pushExpr("test-a0"), pushExpr("test-a1"), pushExpr("test-a2"), pushExpr("test-a3"));

    final int initialDepth = stack.depth();
    assertEquals(stack.peekArg(2), "test-a2");
    assertEquals(stack.depth(), initialDepth);
  }

  @Test
  public void pushArgToStack() {
    StateStack stack = new StateStack();
    open(stack, pushExpr("test-a0"), pushExpr("test-a1"), pushExpr("test-a2"), pushExpr("test-a3"));

    final int initialDepth = stack.depth();
    assertNotEquals(stack.peek(), "test-a2");
    stack.pushArgToStack(2);
    assertEquals(stack.depth(), initialDepth + 1);
    assertEquals(stack.peek(), "test-a2");
  }

  @Test
  public void peekArgBounds() {
    StateStack stack = new StateStack();
    open(stack, pushExpr("test-a0"), pushExpr("test-a1"));

    final int initialDepth = stack.depth();

    assertException(IndexOutOfBoundsException.class, () -> stack.peekArg(-1));
    assertEquals(stack.depth(), initialDepth);

    assertEquals("test-a0", stack.peekArg(0));
    assertEquals(stack.depth(), initialDepth);

    assertEquals("test-a1", stack.peekArg(1));
    assertEquals(stack.depth(), initialDepth);

    assertException(IndexOutOfBoundsException.class, () -> stack.peekArg(2));
    assertEquals(stack.depth(), initialDepth);
  }

  @Test
  public void pushArgToStackBounds() {
    StateStack stack = new StateStack();
    open(stack, pushExpr("test-a0"), pushExpr("test-a1"));

    final int initialDepth = stack.depth();

    assertException(IndexOutOfBoundsException.class, () -> stack.pushArgToStack(-1));
    assertEquals(stack.depth(), initialDepth);

    stack.pushArgToStack(0);
    assertEquals("test-a0", stack.peek());
    assertEquals(stack.depth(), initialDepth + 1);

    stack.pushArgToStack(1);
    assertEquals("test-a1", stack.peek());
    assertEquals(stack.depth(), initialDepth + 2);

    assertException(IndexOutOfBoundsException.class, () -> stack.pushArgToStack(2));
    assertEquals(stack.depth(), initialDepth + 2);
  }

  @Test
  public void pushPeekPop() {
    StateStack stack = new StateStack();
    assertEquals(stack.depth(), 0);

    stack.push("p0");
    assertEquals(stack.peek(), "p0");
    assertEquals(stack.depth(), 1);

    stack.push("p1");
    assertEquals(stack.peek(), "p1");
    assertEquals(stack.depth(), 2);

    stack.push("p2");
    assertEquals(stack.peek(), "p2");
    assertEquals(stack.depth(), 3);

    assertEquals(stack.pop(), "p2");
    assertEquals(stack.depth(), 2);

    assertEquals(stack.pop(), "p1");
    assertEquals(stack.depth(), 1);

    assertEquals(stack.pop(), "p0");
    assertEquals(stack.depth(), 0);

    assertException(IllegalStateException.class, stack::pop);
  }

  @Test
  public void popPastFrame() {
    StateStack stack = new StateStack();
    open(stack);

    assertNotEquals(stack.depth(), 0);
    assertException(IllegalStateException.class, stack::pop);
  }

  @Test
  public void nestedMethods() {
    StateStack stack = new StateStack();

    // call method a
    final int firstCallInitialDepth = stack.depth();
    open(stack, pushExpr("a0"), pushExpr("a1"));

    // call method b
    final int secondCallInitialDepth = stack.depth();
    open(stack, pushExpr("b0"), pushExpr("b1"), pushExpr("b2"));

    // return "rv-b" from method b
    stack.push("rv-b");
    stack.closeFrame();
    assertEquals(stack.depth(), secondCallInitialDepth + 1);
    assertEquals(stack.pop(), "rv-b");

    // return "rv-a" from method a
    stack.push("rv-a");
    stack.closeFrame();
    assertEquals(stack.depth(), firstCallInitialDepth + 1);
    assertEquals(stack.pop(), "rv-a");
  }

  @Test
  public void closeWithoutOpening() {
    StateStack stack = new StateStack();
    assertException(IllegalStateException.class, stack::closeFrame);
  }

  private static void open(StateStack stack, ExecutableElement... args) {
    stack.openFrame(ImmutableList.copyOf(args));
  }
}

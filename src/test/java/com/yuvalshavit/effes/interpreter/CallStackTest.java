package com.yuvalshavit.effes.interpreter;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.Test;

import java.util.function.Consumer;

import static com.yuvalshavit.util.AssertException.assertException;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

public final class CallStackTest {

  @Test
  public void methodInvokeNoArgs() {
    CallStack stack = new CallStack();
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
    CallStack stack = new CallStack();
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
    CallStack stack = new CallStack();
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
    CallStack stack = new CallStack();
    open(stack, pushExpr("test-a0"), pushExpr("test-a1"), pushExpr("test-a2"), pushExpr("test-a3"));

    final int initialDepth = stack.depth();
    assertEquals(stack.peekArg(2), "test-a2");
    assertEquals(stack.depth(), initialDepth);
  }

  @Test
  public void pushArgToStack() {
    CallStack stack = new CallStack();
    open(stack, pushExpr("test-a0"), pushExpr("test-a1"), pushExpr("test-a2"), pushExpr("test-a3"));

    final int initialDepth = stack.depth();
    assertNotEquals(stack.peek(), "test-a2");
    stack.pushArgToStack(2);
    assertEquals(stack.depth(), initialDepth + 1);
    assertEquals(stack.peek(), "test-a2");
  }

  @Test
  public void peekArgBounds() {
    CallStack stack = new CallStack();
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
    CallStack stack = new CallStack();
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
    CallStack stack = new CallStack();
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
    CallStack stack = new CallStack();
    open(stack);

    assertNotEquals(stack.depth(), 0);
    assertException(IllegalStateException.class, stack::pop);
  }

  @Test
  public void nestedMethods() {
    CallStack stack = new CallStack();

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
  public void argAlsoInvokes() {
    CallStack stack = new CallStack();
    ExecutableElement invokingArg = s -> {
      open(stack, pushExpr("innerMethodArg"));
      stack.push("innerMethodRv");
      stack.closeFrame();
    };
    final int initialDepth = stack.depth();
    open(stack, pushExpr("outerMethodA0"), invokingArg);
    assertEquals(stack.peekArg(0), "outerMethodA0");
    assertEquals(stack.peekArg(1), "innerMethodRv");
    stack.push("outerMethodRv");
    stack.closeFrame();
    assertEquals(stack.depth(), initialDepth + 1);
    assertEquals("outerMethodRv", stack.pop());
  }

  @Test
  public void closeWithoutOpening() {
    CallStack stack = new CallStack();
    assertException(IllegalStateException.class, stack::closeFrame);
  }

  @Test
  public void pushLocalToStackWhenFresh() {
    CallStack stack = new CallStack();
    stack.push("Foo");
    stack.push("Bar");
    stack.pushLocalToStack(0);
    assertEquals(stack.pop(), "Foo");
    assertEquals(stack.pop(), "Bar");
    assertEquals(stack.pop(), "Foo");
  }

  @Test
  public void pushLocalToStackWithinFrame() {
    CallStack stack = new CallStack();
    final int initialDepth = stack.depth();
    open(stack, pushExpr("a0"), pushExpr("a1"));

    stack.push("Foo");
    stack.push("Bar");
    stack.pushLocalToStack(0);
    assertEquals(stack.pop(), "Foo"); // from pushLocalToStack
    assertEquals(stack.pop(), "Bar");

    stack.closeFrame();
    assertEquals(stack.pop(), "Foo"); // the "original" one
    assertEquals(stack.depth(), initialDepth);
  }

  @Test
  public void pushLocalToStackBounds() {
    Consumer<Consumer<CallStack>> setupAndRun = action -> {
      CallStack stack = new CallStack();
      stack.push("One");
      stack.push("Two");
      stack.push("Three");
      action.accept(stack);
    };

    setupAndRun.accept(s -> assertException(IndexOutOfBoundsException.class, () -> s.pushLocalToStack(-1)));
    setupAndRun.accept(s -> {
      s.pushLocalToStack(0);
      assertEquals(s.pop(), "One");
    });
    setupAndRun.accept(s -> {
      s.pushLocalToStack(1);
      assertEquals(s.pop(), "Two");
    });
    setupAndRun.accept(s -> {
      s.pushLocalToStack(2);
      assertEquals(s.pop(), "Three");
    });
    setupAndRun.accept(s -> assertException(IndexOutOfBoundsException.class, () -> s.pushLocalToStack(3)));
  }

  @Test
  public void popToLocalWhenFresh() {
    CallStack stack = new CallStack();
    final int initialDepth = stack.depth();
    stack.push("one");
    stack.push("two");
    stack.push("three");
    stack.popToLocal(0);
    assertEquals(stack.pop(), "two");
    assertEquals(stack.pop(), "three"); // from the write
    assertEquals(stack.depth(), initialDepth);
  }

  @Test
  public void popToLocalWithinFrame() {
    CallStack stack = new CallStack();
    final int initialDepth = stack.depth();
    open(stack, pushExpr("a0"), pushExpr("a1"));

    stack.push("one");
    stack.push("two");
    stack.push("three");
    stack.popToLocal(0);
    assertEquals(stack.pop(), "two");

    stack.closeFrame();
    assertEquals(stack.pop(), "three"); // from the write, then rv
    assertEquals(stack.depth(), initialDepth);
  }

  @Test
  public void popToLocalBounds() {
    final int initialDepth;
    {
      CallStack stack = new CallStack();
      initialDepth = stack.depth();
    }
    Consumer<Consumer<CallStack>> setupAndRun = action -> {
      CallStack stack = new CallStack();
      stack.push("One");
      stack.push("Two");
      stack.push("Three");
      action.accept(stack);
    };

    setupAndRun.accept(s -> assertException(IndexOutOfBoundsException.class, () -> s.popToLocal(-1)));
    setupAndRun.accept(s -> {
      s.popToLocal(0);
      assertEquals(s.pop(), "Two");
      assertEquals(s.pop(), "Three");
      assertEquals(s.depth(), initialDepth);
    });
    setupAndRun.accept(s -> {
      s.popToLocal(1);
      assertEquals(s.pop(), "Three");
      assertEquals(s.pop(), "One");
      assertEquals(s.depth(), initialDepth);
    });
    setupAndRun.accept(s -> assertException(IndexOutOfBoundsException.class, () -> s.popToLocal(2)));
    setupAndRun.accept(s -> assertException(IndexOutOfBoundsException.class, () -> s.popToLocal(3)));
  }

  private static void open(CallStack stack, ExecutableElement... args) {
    stack.openFrame(ImmutableList.copyOf(args));
  }
}

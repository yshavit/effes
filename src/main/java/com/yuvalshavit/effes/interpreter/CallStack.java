package com.yuvalshavit.effes.interpreter;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;

import java.util.ArrayList;
import java.util.List;

/**
 * High-level representation of the call stack.
 *
 * To make things easier on myself, I'm going to have this class -- and *not* individual expressions -- responsible for
 * defining the call stack's format and all that. Expressions just need to use the high-level abstractions provided.
 *
 * That format is:
 *  [ return value       ]
 *  [ argN...arg0        ]
 *  [ ArgCount           ] <-- "esb"
 *  [ local vars         ]
 *
 * We don't use an esx register or such because I don't think it'd help much as we go through the JVM; a "register"
 * isn't going to be much faster than just working on the stack directly. Maybe it will... something to try out later.
 * Also, storing the ESB directly in the stack doesn't help much, because we anyways need to pop each element so that
 * the stack's slots can be nulled out for the GC; we can't insta-pop.
 *
 * The "number of args" doubles as a safety check for expressions that pop from the stack; they aren't allowed to pop
 * it.
 */
public final class CallStack {

  private static final Object RV_PLACEHOLDER = "<rv>";
  private int esp = -1;
  private final List<Object> states = new ArrayList<>(); // TODO change to Object[] directly?

  public void openFrame(List<? extends ExecutableElement> args) {
    Esp save = new Esp(states.size() - 1);
    push(RV_PLACEHOLDER);
    int nArgs = args.size();
    int expectedDepth = depth();
    for (int i = nArgs - 1; i >= 0; --i) {
      args.get(i).execute(this);
      if (++expectedDepth != depth()) {
        throw new IllegalArgumentException("expression " + i + " didn't push exactly one state: " + args.get(i));
      }
    }
    push(save);
    esp = states.size() - 1;
  }

  public void closeFrame() {
    if (states.size() <= 2) { // should have the RV_PLACEHOLDER and ArgsCount at least
      throw new IllegalStateException("no frame to close");
    }

    Object atEsp = states.get(esp);
    // top of stack is rv
    Object rv = pop();
    // pop off the local vars and esb
    Object popped;
    do {
      popped = uncheckedPop();
    } while (!(popped instanceof Esp));
    Esp lastEsp = (Esp) popped;

    // pop off the frame. We add 2 because (a) size() is 1-indexed and (b) we need room for the rv
    states.set(lastEsp.targetIndex + 1, rv); // set the rv
    while (states.size() > lastEsp.targetIndex + 2) {
      states.remove(states.size() - 1);
    }

    this.esp = lastEsp.targetIndex;
  }

  public void pushArgToStack(int pos) {
    push(peekArg(pos));
  }

  public Object peekArg(int pos) {
    return states.get(esp - pos - 1);
  }

  /**
   * Pushes a local variable (ie, one on the stack) to the top of the stack.
   * @param pos 0-indexed, where 0 is the first variable you pushed; the variable to read from
   * @throws IllegalArgumentException if pos is negative or extends beyond the current stack size
   */
  public void pushLocalToStack(int pos) {
    if (pos < 0) {
      throw new IndexOutOfBoundsException(Integer.toString(pos));
    }
    push(states.get(esp + pos + 1));
  }

  /**
   * Pops the top of the stack, and writes it to a local var slot. Note that the pop happens before the write, so
   * if you pass the pos of the stack head, you'll get an {@code IllegalArgumentException} because that slot has
   * been popped by the time the write is attempted
   * @param pos 0-indexed, where 0 is the first variable you pushed; the variable to write to
   * @throws IllegalArgumentException if pos is negative or extends beyond the current stack size
   */
  public void popToLocal(int pos) {
    if (pos < 0) {
      throw new IndexOutOfBoundsException(Integer.toString(pos));
    }
    states.set(esp + pos + 1, pop());
  }

  public void push(Object state) {
    states.add(state);
  }

  public Object pop() {
    Object r = uncheckedPop();
    if (Esp.class.equals(r.getClass())) {
      push(r);
      throw new IllegalStateException("can't pop past frame");
    }
    return r;
  }

  public Object peek() {
    return states.get(states.size() - 1);
  }

  @Override
  public String toString() {
    int depth = states.size();
    if (depth == 0) {
      return "[]";
    }
    List<String> elems = new ArrayList<>(depth);
    for (int i = depth - 1; i >= 0; --i) {
      String fmt = (i == esp)
        ? "%d. {%s}"
        : "%d. %s";
      elems.add(String.format(fmt, i, states.get(i)));
    }
    return String.format("[ %s ]", Joiner.on(" // ").join(elems));
  }

  private Object uncheckedPop() {
    try {
      return states.remove(states.size() - 1);
    } catch (Exception e) {
      throw new IllegalStateException();
    }
  }

  @VisibleForTesting
  int depth() {
    return states.size();
  }

  private static class Esp {

    @Override
    public String toString() {
      return String.format("esp=%d", targetIndex);
    }

    private final int targetIndex;

    private Esp(int targetIndex) {
      this.targetIndex = targetIndex;
    }
  }
}

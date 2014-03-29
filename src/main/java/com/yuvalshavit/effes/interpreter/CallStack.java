package com.yuvalshavit.effes.interpreter;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

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
 *  [ FrameInfo          ] <-- "fip"
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
  private int fip = -1;
  private final List<Object> states = new ArrayList<>(); // TODO change to Object[] directly?

  public void openFrame(List<? extends ExecutableElement> args) {
    push(RV_PLACEHOLDER);
    FrameInfo frameInfo = new FrameInfo(args.size(), fip, states.size());
    int nArgs = args.size();
    int expectedDepth = states.size();
    for (int i = nArgs - 1; i >= 0; --i) {
      args.get(i).execute(this);
      if (++expectedDepth != states.size()) {
        throw new IllegalArgumentException("expression " + i + " didn't push exactly one state: " + args.get(i));
      }
    }
    push(frameInfo);
    fip = states.size() - 1;
  }

  public void closeFrame() {
    if (states.size() <= 2) { // should have the RV_PLACEHOLDER and ArgsCount at least
      throw new IllegalStateException("no frame to close");
    }
    assert fip == -1 || (states.get(fip) instanceof FrameInfo) : states.get(fip);

    popToRv(); // TODO return statement should probably do this, maybe?
    FrameInfo frameInfo = frameInfo();
    int targetSize = frameInfo.prevSp; // includes the rv
    while (states.size() > targetSize) {
      uncheckedPop();
    }
    this.fip = frameInfo.prevFip;
    assert fip == -1 || (states.get(fip) instanceof FrameInfo) : states.get(fip);
  }

  public void pushArgToStack(int pos) {
    push(peekArg(pos));
  }

  public Object peekArg(int pos) {
    int n = frameInfo().nArgs;
    if (pos < 0 || pos >= n) {
      throw new IndexOutOfBoundsException(String.format("invalid arg %d for frame at fip %d (nArgs=%d)", pos, fip, n));
    }
    return states.get(fip - pos - 1);
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
    push(states.get(fip + pos + 1));
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
    states.set(fip + pos + 1, pop());
  }

  public void push(Object state) {
    states.add(state);
  }

  public Object pop() {
    Object r = uncheckedPop();
    if (r instanceof FrameInfo) {
      push(r);
      throw new IllegalStateException("can't pop past frame");
    }
    return r;
  }

  public Object peek() {
    return states.get(states.size() - 1);
  }

  private void popToRv() {
    states.set(fip - frameInfo().nArgs - 1, pop());
  }

  private FrameInfo frameInfo() {
    return (FrameInfo) states.get(fip);
  }

  @Override
  public String toString() {
    int depth = states.size();
    if (depth == 0) {
      return "[]";
    }
    List<String> elems = new ArrayList<>(depth);
    for (int i = depth - 1; i >= 0; --i) {
      String fmt = (i == fip)
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
  Object snapshot() {
    return ImmutableMap.of("fip", fip, "stack", ImmutableList.copyOf(states));
  }

  private static class FrameInfo {

    private final int nArgs;
    private final int prevFip;
    private final int prevSp;

    private FrameInfo(int nArgs, int prevFip, int prevSp) {
      this.nArgs = nArgs;
      this.prevFip = prevFip;
      this.prevSp = prevSp;
    }

    @Override
    public String toString() {
      return String.format("sp:%d, fip:%d, args:%d", prevSp, prevFip, nArgs);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      FrameInfo frameInfo = (FrameInfo) o;
      return nArgs == frameInfo.nArgs && prevFip == frameInfo.prevFip && prevSp == frameInfo.prevSp;
    }

    @Override
    public int hashCode() {
      int result = nArgs;
      result = 31 * result + prevFip;
      result = 31 * result + prevSp;
      return result;
    }
  }
}

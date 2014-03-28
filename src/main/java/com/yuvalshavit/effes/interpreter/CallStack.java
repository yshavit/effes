package com.yuvalshavit.effes.interpreter;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

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
  private int esb = -1;
  private final List<Object> states = new ArrayList<>(); // TODO change to Object[] directly?

  public void openFrame(List<? extends ExecutableElement> args) {
    ArgsCount save = new ArgsCount(states.size() - 1);
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
    esb = states.size() - 1;
  }

  public void closeFrame() {
    if (states.size() <= 2) { // should have the RV_PLACEHOLDER and ArgsCount at least
      throw new IllegalStateException("no frame to close");
    }

    // top of stack is rv
    Object rv = pop();

    // pop off the local vars and esb
    Object popped;
    do {
      popped = uncheckedPop();
    } while (!(popped instanceof ArgsCount));
    ArgsCount argsCount = (ArgsCount) popped;

    // pop off the frame
    while (states.size() > argsCount.targetSize + 1) { // todo can write rv to targetSize + 1, then pop until > targetsize+2
      states.remove(states.size() - 1);
    }
    esb = argsCount.targetSize;

    // finally, re-push that rv
    push(rv);
  }

  public void pushArgToStack(int pos) {
    push(peekArg(pos));
  }

  public Object peekArg(int pos) {
    checkArgPos(pos);
    return states.get(esb - pos - 1);
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
    push(states.get(esb + pos + 1));
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
    states.set(esb + pos + 1, pop());
  }

  public void push(Object state) {
    states.add(state);
  }

  public Object pop() {
    Object r = uncheckedPop();
    if (ArgsCount.class.equals(r.getClass())) {
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
    if ("1".equals("1")) {
      return String.format("(esb %d) %s", esb, Lists.reverse(states));
    }
    final int LOCAL_STARTING = -1;
    final int LOCAL_CONT = -2;
    final int RV = -3;
    // (esb *) 3:[local(v2, v1) nArgs(2)* args(a1, a0) rv(null)] 2:[...

    // First, get each individual frame, last to first
    List<String> frameDescriptions = new ArrayList<>();
    int modeOrArgs = LOCAL_STARTING;
    StringBuilder sb = new StringBuilder();
    for (int i = states.size() - 1; i >= 0; --i) {
      Object elem = states.get(i);
      if (elem instanceof ArgsCount) {
        // close the parens if we'd had at least one local
        if (modeOrArgs == LOCAL_CONT) {
          sb.append("} ");
        }
        modeOrArgs = 666; //((ArgsCount) elem).count;
        if (i == esb) {
          sb.append('*');
        }
        sb.append("nArgs{").append(modeOrArgs).append('}');
        if (modeOrArgs == 0) {
          sb.append(' ');
          modeOrArgs = RV;
        } else {
          sb.append(" args{");
        }
      } else if (modeOrArgs > 1) {
        sb.append(elem).append(", ");
        --modeOrArgs;
      } else if (modeOrArgs == 1) {
        sb.append(elem).append("} ");
        modeOrArgs = RV;
      } else if (modeOrArgs == LOCAL_STARTING) {
        sb.append("local{").append(elem);
        modeOrArgs = LOCAL_CONT;
      } else if (modeOrArgs == LOCAL_CONT) {
        sb.append(", ").append(elem);
      } else if (modeOrArgs == RV) {
        sb.append("rv{").append(elem).append("}");
        frameDescriptions.add(sb.toString());
        sb.setLength(0);
      }
    }
    if (sb.length() > 0) {
      frameDescriptions.add(sb.toString());
      sb.setLength(0);
    }

    // Transform "frame desc" to "3:[frame desc]" where the number is the frame's depth
    for (int i = 0, nFrames = frameDescriptions.size(); i < nFrames; ++i) {
      String desc = frameDescriptions.get(i);
      desc = String.format("$%d$ %s", nFrames - i, desc);
      frameDescriptions.set(i, desc);
    }
    return String.format("(esb %d) %s", esb, Joiner.on(" \\\\ ").join(frameDescriptions));
  }

  private Object uncheckedPop() {
    try {
      return states.remove(states.size() - 1);
    } catch (Exception e) {
      throw new IllegalStateException();
    }
  }

  private void checkArgPos(int pos) {
//    if (pos < 0 || pos >= currentFrameArgsCount()) {
//      throw new IndexOutOfBoundsException(Integer.toString(pos));
//    }
  }

  @VisibleForTesting
  int currentFrameArgsCount() {
    throw new UnsupportedOperationException(); // TODO
//    return ((ArgsCount) states.get(esb)).count;
  }

  @VisibleForTesting
  int depth() {
    return states.size();
  }

  private static class ArgsCount {

    @Override
    public String toString() {
      return String.format("{esb=%d}", targetSize);
    }

    private final int targetSize;

    private ArgsCount(int targetSize) {
      this.targetSize = targetSize;
    }
  }
}

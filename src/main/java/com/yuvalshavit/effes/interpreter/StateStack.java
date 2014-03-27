package com.yuvalshavit.effes.interpreter;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

/**
 * High-level representation of the call stack.
 *
 * To make things easier on myself, I'm going to have this class -- and *not* individual expressions -- responsible for
 * defining the call stack's format and all that. Expressions just need to use the high-level abstractions provided.
 *
 * That format is:
 *  [ return value       ] <-- esb
 *  [ argN...arg0        ]
 *  [ ArgCount           ]
 *  [ expression scratch ]
 *
 * We don't use an esx register or such because I don't think it'd help much as we go through the JVM; a "register"
 * isn't going to be much faster than just working on the stack directly. Maybe it will... something to try out later.
 * Also, storing the ESB directly in the stack doesn't help much, because we anyways need to pop each element so that
 * the stack's slots can be nulled out for the GC; we can't insta-pop.
 *
 * The "number of args" doubles as a safety check for expressions that pop from the stack; they aren't allowed to pop
 * it.
 */
public final class StateStack { // TODO rename to CallStack

  private static final Object RV_PLACEHOLDER = null;
  private int esb = -1;
  private final List<Object> states = new ArrayList<>(); // TODO change to Object[] directly?

  public void openFrame(List<? extends ExecutableElement> args) {
    push(RV_PLACEHOLDER);
    int nArgs = args.size();
    int expectedDepth = depth();
    for (int i = nArgs - 1; i >= 0; --i) {
      args.get(i).execute(this);
      if (++expectedDepth != depth()) {
        throw new IllegalArgumentException("expression " + i + " didn't push exactly one state: " + args.get(i));
      }
    }
    push(ArgsCount.of(nArgs));
    esb += (nArgs + 2);
  }

  public void closeFrame() {
    if (states.size() <= 2) { // should have the RV_PLACEHOLDER and ArgsCount at least
      throw new IllegalStateException("no frame to close");
    }
    states.set(esb - currentFrameArgsCount() - 1, uncheckedPop());
    ArgsCount argsCount = (ArgsCount) uncheckedPop();
    int nArgs = argsCount.count;
    for (int i = 0; i < nArgs; ++i) {
      states.remove(states.size() - 1);
    }
    esb -= (nArgs + 2);
  }

  public void pushArgToStack(int pos) {
    push(peekArg(pos));
  }

  public Object peekArg(int pos) {
    checkArgPos(pos);
    return states.get(esb - pos - 1);
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
    return String.format("(esb %d) state=%s", esb, states);
  }

  private Object uncheckedPop() {
    try {
      return states.remove(states.size() - 1);
    } catch (Exception e) {
      throw new IllegalStateException();
    }
  }

  private void checkArgPos(int pos) {
    if (pos < 0 || pos >= currentFrameArgsCount()) {
      throw new IndexOutOfBoundsException(Integer.toString(pos));
    }
  }

  @VisibleForTesting
  int currentFrameArgsCount() {
    return ((ArgsCount) states.get(esb)).count;
  }

  @VisibleForTesting
  int depth() {
    return states.size();
  }

  private static class ArgsCount {

    static ArgsCount of(int n) {
      return n < cached.length
        ? cached[n]
        : new ArgsCount(n);
    }

    private static final ArgsCount[] cached = createCached(10);

    @Override
    public String toString() {
      return String.format("{nArgs=%d}", count);
    }

    private static ArgsCount[] createCached(int n) {
      ArgsCount[] arr = new ArgsCount[n];
      for (int i = 0; i < arr.length; ++i) {
        arr[i] = new ArgsCount(i);
      }
      return arr;
    }

    private final int count;

    private ArgsCount(int count) {
      this.count = count;
    }
  }
}

package com.yuvalshavit.effes.compile.pmatch;

import java.util.List;

import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.util.EfFunctions;
import com.yuvalshavit.util.Lazy;

public class PPossibilityStringer implements PPossibilityVisitor {
  private final boolean verbose;
  private final StringBuilder sb = new StringBuilder();

  public PPossibilityStringer(boolean verbose) {
    this.verbose = verbose;
  }

  @Override
  public void visit(PPossibility.Simple simple) {
    simple.typedAndArgs().consume(
      large -> toString(large.type()),
      std -> {
        List<LazyPossibility> args = std.args();
        if (args.isEmpty()) {
          toString(std.type());
        } else {
          toString(std.type());
          sb.append('(');
          EfFunctions.interleaveC(
            std.args(),
            arg -> {
              Lazy<PPossibility> argLazy = arg.possibility();
              if (argLazy.isUnforced()) {
                sb.append(Lazy.UNFORCED_DESC);
              } else {
                argLazy.get().accept(this);
              }
            },
            () -> sb.append(", ")
          );
          sb.append(')');
        }
      });
  }

  @Override
  public void visit(PPossibility.Disjunction disjunction) {
    EfFunctions.interleaveC(disjunction.options(), this::visit, () -> sb.append(" | "));
  }

  @Override
  public void visitNone() {
    sb.append('∅');
  }

  @Override
  public String toString() {
    return sb.toString();
  }

  private void toString(EfType.SimpleType type) {
    String s = verbose
      ? type.toString()
      : type.getName();
    sb.append(s);
  }
}

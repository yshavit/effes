package com.yuvalshavit.effes.compile.pmatch;

import java.util.List;
import java.util.function.Function;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.util.EfFunctions;
import com.yuvalshavit.util.GreekCounter;
import com.yuvalshavit.util.Lazy;

public class PPossibilityStringer implements PPossibilityVisitor {
  private final GreekCounter.Assigner<EfType> typeAssigner;
  private final StringBuilder sb = new StringBuilder();
  
  public PPossibilityStringer(GreekCounter.Assigner<EfType> typeAssigner) {
    this.typeAssigner = typeAssigner;
  }
  
  public PPossibilityStringer() {
    this(new GreekCounter.Assigner<>());
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
    sb.append(type.getName());
    List<EfType> params = type.getParams();
    if (!params.isEmpty()) {
      sb.append('[');
      List<String> paramDescriptions = Lists.transform(params, typeAssigner::apply);
      Joiner.on(", ").appendTo(sb, paramDescriptions);
      sb.append(']');
    }
  }

  public static Function<? super PPossibility, String> usingAssigner(GreekCounter.Assigner<EfType> assigner) {
    return p -> {
      PPossibilityStringer stringer = new PPossibilityStringer(assigner);
      p.accept(stringer);
      return stringer.toString();
    };
  }
}

package com.yuvalshavit.effes.compile.pmatch;

import java.util.List;
import java.util.function.Function;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.util.EfFunctions;
import com.yuvalshavit.util.GreekMapping;
import com.yuvalshavit.util.Lazy;
import com.yuvalshavit.util.Mapping;

public class PPossibilityStringer implements PPossibilityVisitor {
  private final Function<EfType, String> typeAssigner;
  private final StringBuilder sb = new StringBuilder();
  
  public PPossibilityStringer(Function<EfType, String> efTypeStringer) {
    this.typeAssigner = efTypeStringer;
  }
  
  public PPossibilityStringer() {
    this(new GreekMapping<>());
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

  public static Function<? super PPossibility, String> usingMapping(Mapping<EfType, String> assigner) {
    return p -> {
      PPossibilityStringer stringer = new PPossibilityStringer(assigner);
      p.accept(stringer);
      return stringer.toString();
    };
  }
}

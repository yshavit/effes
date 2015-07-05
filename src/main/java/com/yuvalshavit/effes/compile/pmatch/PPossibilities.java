package com.yuvalshavit.effes.compile.pmatch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.google.common.collect.Lists;
import com.yuvalshavit.effes.compile.node.EfType;
import com.yuvalshavit.util.Lazy;

public class PPossibilities {
  private PPossibilities() {}
  
  public static List<String> toStrings(PPossibility possibility) {
    List<String> ret = new ArrayList<>(possibility.dispatch(
      d -> Lists.transform(d.options(), Object::toString),
      s -> Collections.singletonList(s.toString()),
      () -> Collections.singletonList("∅")));
    Collections.sort(ret);
    return ret;
  }

  @SuppressWarnings("unused")
  public static String displayForDebugging(Collection<List<Lazy<PPossibility>>> argCombos) {
    StringBuilder sb = new StringBuilder();
    for (List<Lazy<PPossibility>> args : argCombos) {
      displayArgs(args, sb);
      sb.append('\n');
    }
    return sb.toString();
  }

  private static void displayArgs(List<Lazy<PPossibility>> args, StringBuilder sb) {
    sb.append('[');
    for (Iterator<Lazy<PPossibility>> iter = args.iterator(); iter.hasNext(); ) {
      displayLazyPossibility(iter.next(), sb);
      if (iter.hasNext()) {
        sb.append(", ");
      }
    }
    sb.append(']');
  }

  private static void displayLazyPossibility(Lazy<PPossibility> arg, StringBuilder sb) {
    if (arg.isUnforced()) {
      sb.append('<').append(Integer.toHexString(arg.getId())).append('>');
    } else {
      PPossibility possibility = arg.get();
      displayPossibility(possibility, sb);
    }
  }

  private static void displayPossibility(PPossibility possibility, StringBuilder sb) {
    possibility.consume(
      d -> displayDisjunction(d.options(), sb),
      s -> displayTyped(s.typedAndArgs(), sb),
      () -> sb.append("∅"));
  }

  private static void displayTyped(TypedValue<Lazy<PPossibility>> typed, StringBuilder sb) {
    List<Lazy<PPossibility>> simpleArgs = typed.transform(
      large -> Collections.<Lazy<PPossibility>>emptyList(),
      TypedValue.StandardValue::args
    );
    displaySimple(typed.type(), simpleArgs, sb);
  }

  private static void displaySimple(EfType.SimpleType type, List<Lazy<PPossibility>> args, StringBuilder sb) {
    sb.append(type.getName());
    if (!args.isEmpty()) {
      sb.append('(');
      for (Iterator<Lazy<PPossibility>> iter = args.iterator(); iter.hasNext(); ) {
        displayLazyPossibility(iter.next(), sb);
        if (iter.hasNext()) {
          sb.append(", ");
        }
      }
      sb.append(')');
    }
  }

  private static void displayDisjunction(List<PPossibility.Simple> options, StringBuilder sb) {
    for (Iterator<PPossibility.Simple> iter = options.iterator(); iter.hasNext(); ) {
      PPossibility.Simple option = iter.next();
      displayPossibility(option, sb);
      if (iter.hasNext()) {
        sb.append(" | ");
      }
    }
  }
}

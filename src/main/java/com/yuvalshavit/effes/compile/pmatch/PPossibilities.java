package com.yuvalshavit.effes.compile.pmatch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.google.common.collect.Collections2;
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

  public static PPossibility clean(PPossibility possibility, EfType type) {
    return possibility.dispatch(
      disjunction -> cleanDisjunction(disjunction, type),
      simple -> cleanSimple(simple, type),
      () -> PPossibility.none
    );
  }

  private static PPossibility.Simple cleanSimple(PPossibility.Simple simple, EfType type) {
    throw new UnsupportedOperationException(); // TODO
  }

  private static PPossibility cleanDisjunction(PPossibility.Disjunction disjunction, EfType type) {
    Collection<PPossibility.Simple> cleanedOptions = Collections2.transform(disjunction.options(), option -> cleanSimple(option, type));
    return new PPossibility.Disjunction(cleanedOptions);
  }

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
    possibility.dispatch(
      d -> displayDisjunction(d.options(), sb),
      s -> displayTyped(s.typedAndArgs(), sb),
      () -> sb.append("∅"));
  }

  private static Void displayTyped(TypedValue<Lazy<PPossibility>> typed, StringBuilder sb) {
    List<Lazy<PPossibility>> simpleArgs = typed.transform(
      large -> Collections.<Lazy<PPossibility>>emptyList(),
      TypedValue.StandardValue::args
    );
    return displaySimple(typed.type(), simpleArgs, sb);
  }

  private static Void displaySimple(EfType.SimpleType type, List<Lazy<PPossibility>> args, StringBuilder sb) {
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
    return null;
  }

  private static Void displayDisjunction(List<PPossibility.Simple> options, StringBuilder sb) {
    for (Iterator<PPossibility.Simple> iter = options.iterator(); iter.hasNext(); ) {
      PPossibility.Simple option = iter.next();
      displayPossibility(option, sb);
      if (iter.hasNext()) {
        sb.append(" | ");
      }
    }
    return null;
  }
}

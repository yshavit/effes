package com.yuvalshavit.effes.compile;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class EfArgs {
  private final List<Arg> args;

  private EfArgs(List<Arg> args) {
    this.args = ImmutableList.copyOf(args);
  }

  public List<EfType> viewTypes() {
    return Collections.unmodifiableList(args.stream().map(Arg::type).collect(Collectors.toList()));
  }

  public List<Arg> asList() {
    return args;
  }

  public int length() {
    return args.size();
  }

  @Override
  public String toString() {
    return Joiner.on(", ").join(args);
  }

  public static class Arg {
    private final String name;
    private final EfType type;

    private Arg(String name, EfType type) {
      this.name = name;
      this.type = type;
    }

    public String name() {
      return name;
    }

    public EfType type() {
      return type;
    }

    @Override
    public String toString() {
      return name != null
        ? name + ' ' + type
        : String.valueOf(type);
    }
  }

  public static class Builder {
    private final CompileErrors errs;
    private final List<Arg> args = new ArrayList<>();
    private final Set<String> names = new HashSet<>();

    public Builder(CompileErrors errs) {
      this.errs = errs;
    }

    public void add(Token token, String name, EfType type) {
      if (name != null && (!names.add(name))) {
        errs.add(token, "duplicate argument name: " + name);
      }
      args.add(new Arg(name, type));
    }

    public EfArgs build() {
      return new EfArgs(args);
    }

    @Override
    public String toString() {
      String str = args.toString();
      if (errs != null && errs.hasErrors()) {
        int nErrs = errs.getErrors().size();
        str = String.format("%s (%d error%s)", str, nErrs, nErrs == 1 ? "" : "s");
      }
      return str;
    }
  }
}

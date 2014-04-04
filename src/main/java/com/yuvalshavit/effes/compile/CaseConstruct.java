package com.yuvalshavit.effes.compile;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.antlr.v4.runtime.Token;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CaseConstruct {

  private final Expression matchAgainst;
  private final List<Alternative> patterns;
  private final EfType resultType;

  public CaseConstruct(Expression matchAgainst, List<Alternative> patterns) {
    this.matchAgainst = matchAgainst;
    this.patterns = ImmutableList.copyOf(patterns);
    this.resultType = computeType(patterns);
  }

  public Expression getMatchAgainst() {
    return matchAgainst;
  }

  public List<Alternative> getPatterns() {
    return patterns;
  }

  public EfType resultType() {
    return resultType;
  }

  public void validate(Token token, CompileErrors errs) {
    // Validate that:
    // 1) each matcher *can* match the given expression
    // 2) at least one matcher *will* match the given expression
    // 3) each matcher is reachable (ie, the ones before it may not match)
    // 4) each matcher matches the right number of args
    matchAgainst.validate(errs);

    EfType matchType = matchAgainst.resultType();
    if (matchType instanceof EfType.DisjunctiveType) {
      EfType.DisjunctiveType dis = (EfType.DisjunctiveType) matchType;
      Set<EfType> matchAgainstTypes = dis.getAlternatives();
      Set<EfType> patternTypes = patterns
        .stream()
        .map(Alternative::getType)
        .collect(Collectors.toSet());
      // extra types
      Sets.difference(patternTypes, matchAgainstTypes).forEach(t -> errs.add(
        matchAgainst.token(),
        String.format("pattern type (%s) can never match expression type (%s)", t, matchType)));
      // missing types
      Sets.difference(matchAgainstTypes, patternTypes).forEach(t -> errs.add(
        matchAgainst.token(),
        "expression alternative is never matched: " + t));
      patterns.forEach(alt -> {
        int expected = alt.getType().getArgs().size();
        int actual = alt.getBindings().size();
        if (expected != actual) {
          String plural = expected == 1 ? "" : "s";
          errs.add(token, String.format("expected %d binding%s but found %d", expected, plural, actual));
        }
      });
    } else {
      errs.add(token, "case requires a disjunctive type (found " + matchType + ")");
    }
  }

  public void state(NodeStateListener out) {
    out.child("case", matchAgainst);
    patterns.forEach(p -> out.child("of " + p.getType(), p.getIfMatched()));
  }

  @Override
  public String toString() {
    return String.format("case (%s) of...", matchAgainst);
  }

  private static EfType computeType(List<Alternative> patterns) {
    EfType result = null;
    for (Alternative p : patterns) {
      EfType patternResult = p.getIfMatched().resultType();
      result = result != null
        ? EfType.disjunction(result, patternResult)
        : patternResult;
    }
    return result;
  }

  public static class Alternative {
    private final EfType.SimpleType type;
    private final Expression ifMatched;
    private final List<EfVar> bindings;

    public Alternative(EfType.SimpleType type, List<EfVar> bindings, Expression ifMatched) {
      this.type = type;
      this.ifMatched = ifMatched;
      this.bindings = ImmutableList.copyOf(bindings);
    }

    public Expression getIfMatched() {
      return ifMatched;
    }

    public EfType.SimpleType getType() {
      return type;
    }

    public List<EfVar> getBindings() {
      return bindings;
    }
  }
}

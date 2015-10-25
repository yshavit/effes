package com.yuvalshavit.effes.compile.pmatch;

public interface PPossibilityVisitor {
  void visit(PPossibility.Simple simple);
  void visit(PPossibility.Disjunction disjunction);
  void visitNone();
}

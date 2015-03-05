package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.parser.EffesParser;

public class Source {
  private final boolean isBuiltin;
  private final EffesParser.CompilationUnitContext parseUnit;

  Source(EffesParser.CompilationUnitContext parseUnit, boolean isBuiltin) {
    this.isBuiltin = isBuiltin;
    this.parseUnit = parseUnit;
  }
  
  public Source(EffesParser.CompilationUnitContext parseUnit) {
    this(parseUnit, false);
  }

  public boolean isBuiltin() {
    return isBuiltin;
  }

  public EffesParser.CompilationUnitContext getParseUnit() {
    return parseUnit;
  }
}

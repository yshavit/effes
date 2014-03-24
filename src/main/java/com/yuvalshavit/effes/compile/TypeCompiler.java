package com.yuvalshavit.effes.compile;

import com.yuvalshavit.effes.base.Type;
import com.yuvalshavit.effes.parser.EffesParser;

public interface TypeCompiler {
  Type aooly(EffesParser.TypeContext ctx);
}

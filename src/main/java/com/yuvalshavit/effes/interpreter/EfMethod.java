package com.yuvalshavit.effes.interpreter;

import java.util.List;

public interface EfMethod {
  EfVariable invoke(EfVariable target, List<EfVariable> args);
}

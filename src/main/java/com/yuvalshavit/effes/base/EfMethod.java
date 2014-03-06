package com.yuvalshavit.effes.base;

import java.util.List;

public interface EfMethod {
  EfVariable invoke(EfVariable target, List<EfVariable> args);
}

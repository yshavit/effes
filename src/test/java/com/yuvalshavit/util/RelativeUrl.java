package com.yuvalshavit.util;

import com.google.common.base.Joiner;
import com.google.common.io.Resources;

import java.io.File;
import java.net.URL;

public final class RelativeUrl {
  private final String pathBase;
  private final Class<?> anchor;

  public RelativeUrl(Class<?> anchor) {
    this.anchor = anchor;
    pathBase = anchor.getPackage().getName().replace('.', File.separatorChar);
  }

  public URL get(String... fileNameSegments) {
    String fileName = Joiner.on(File.separatorChar).join(fileNameSegments);
    return Resources.getResource(pathBase + File.separator + fileName);
  }
}

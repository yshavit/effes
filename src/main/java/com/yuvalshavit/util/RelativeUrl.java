package com.yuvalshavit.util;

import com.google.common.base.Joiner;
import com.google.common.io.Resources;

import java.io.File;
import java.net.URL;

public final class RelativeUrl {
  private final String pathBase;

  public RelativeUrl(Class<?> anchor) {
    pathBase = anchor.getPackage().getName().replace('.', File.separatorChar);
  }

  public URL get(String... fileNameSegments) {
    String fileName = Joiner.on(File.separatorChar).join(fileNameSegments);
    String fullName = pathBase + File.separator + fileName;
    try {
      return Resources.getResource(fullName);
    } catch (IllegalArgumentException e) {
      if (String.format("resource %s not found.", fullName).equals(e.getMessage())) {
        return null;
      }
      throw e;
    }
  }
}

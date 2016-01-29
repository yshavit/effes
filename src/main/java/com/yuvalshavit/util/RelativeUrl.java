package com.yuvalshavit.util;

import com.google.common.base.Joiner;
import com.google.common.io.Resources;

import java.io.File;
import java.net.URL;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class RelativeUrl {
  private final String pathBase;

  public RelativeUrl(Class<?> anchor) {
    pathBase = anchor.getPackage().getName().replace('.', File.separatorChar);
  }

  @Nullable
  public URL tryGet(String... fileNameSegments) {
    return getInternal(fileNameSegments, false);
  }

  @Nonnull
  public URL get(String... fileNameSegments) {
    URL result = getInternal(fileNameSegments, true);
    assert result != null : Joiner.on(File.separatorChar).join(fileNameSegments);
    return result;
  }

  private URL getInternal(String[] fileNameSegments, boolean throwIfMissing) {
    String fileName = Joiner.on(File.separatorChar).join(fileNameSegments);
    String fullName = pathBase + File.separator + fileName;
    try {
      return Resources.getResource(fullName);
    } catch (IllegalArgumentException e) {
      if ((!throwIfMissing) && String.format("resource %s not found.", fullName).equals(e.getMessage())) {
        return null;
      }
      throw e;
    }
  }
}

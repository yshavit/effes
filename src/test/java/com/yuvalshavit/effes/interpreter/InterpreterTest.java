package com.yuvalshavit.effes.interpreter;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;
import com.yuvalshavit.util.RelativeUrl;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;

public final class InterpreterTest {
  private static final RelativeUrl url = new RelativeUrl(InterpreterTest.class);
  private static final String provider = "p0";

  @DataProvider(name = provider)
  public static Object[][] testFiles() throws IOException {
    Set<String> baseNames = new HashSet<>();
    for (String fileName : Resources.readLines(url.get("test"), Charsets.UTF_8)) {
      String[] fileAndExt = fileName.split("\\.", 2);
      String ext = fileAndExt[1];
      if ("ef".equals(ext) || "out".equals(ext)) {
        String base = fileAndExt[0];
        baseNames.add(base);
      }
    }
    return baseNames.stream().map(name -> new Object[] { name }).collect(Collectors.toList()).toArray(new Object[0][]);
  }

  @Test(dataProvider = provider)
  public void run(String baseName) throws IOException {
    String efFileName = baseName + ".ef";
    String expectedFileName = baseName + ".out";
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (InputStream is = url.get("test", efFileName).openStream()) {
      EffesParser parser = ParserUtils.createParser(new InputStreamReader(is));
      PrintStream outStream = new PrintStream(baos);
      Interpreter interpreter = new Interpreter(parser.compilationUnit(), outStream);
      interpreter.runMain();
      outStream.flush();
    }
    byte[] expectedBytes = Resources.toByteArray(url.get("test", expectedFileName));
    assertEquals(baos.toString("UTF-8"), new String(expectedBytes, "UTF-8"));
    assertEquals(baos.toByteArray(), expectedBytes); // double-check at the byte level
  }
}

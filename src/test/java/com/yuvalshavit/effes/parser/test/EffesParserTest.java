package com.yuvalshavit.effes.parser.test;

import com.beust.jcommander.internal.Lists;
import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.yuvalshavit.effes.parser.EffesLexer;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.TokenSource;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.testng.Assert.assertEquals;

public final class EffesParserTest {
  private static final String pathBase = EffesParserTest.class.getPackage().getName().replace('.', '/');
  private static final ParserUtils.LiteralsInvoker literalsInvoker = new ParserUtils.LiteralsInvoker(EffesParser.class);

  public static final String PARSE_TESTS = "test1";

  @DataProvider(name = "test1")
  public Object[][] readParseFiles() {
    String testCasePatternStr = System.getProperty("test.case.regex");
    Pattern testCasePattern = testCasePatternStr != null
      ? Pattern.compile(testCasePatternStr)
      : null;
    Set<String> dirs = Sets.newTreeSet();
    Collections.addAll(dirs, readFile(".").split("\n"));

    List<Object[]> parseTests = Lists.newArrayList();
    for (String dir : dirs) {
      URL dirUrl = url(dir);
      if (new File(dirUrl.getFile()).isDirectory()) {
        Set<String> files = Sets.newTreeSet();
        for (String file : readFile(dirUrl).split("\n")) {
          if (file.endsWith(".ef")) {
            String fullFile = dir + File.separator + file;
            if (testCasePattern == null || testCasePattern.matcher(fullFile).find()) {
              String plainFile = fullFile.substring(0, fullFile.length() - ".ef".length());
              parseTests.add(new Object[]{ plainFile });
            }
          }
          files.add(String.format("%s/%s", dir, file));
        }
      }
    }
    return parseTests.toArray(new Object[parseTests.size()][]);
  }

  private String readFile(String relativePath) {
    return readFile(url(relativePath));
  }

  private String readFile(URL url) {
    try {
      return Resources.toString(url, Charsets.UTF_8);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  @Test(dataProvider = PARSE_TESTS)
  public void parse(String fileNameNoExt) throws IOException, ReflectiveOperationException {
    assertThat("file name", fileNameNoExt, containsString(File.separator));
    String expectedContent;
    try {
      expectedContent = readFile(fileNameNoExt + ".tree");
    } catch (IllegalArgumentException e) {
      if (e.getMessage() != null && e.getMessage().endsWith("not found.")) {
        // if the .tree file isn't found, just set expectedContent to null for easy prototyping
        expectedContent = null;
      } else {
        throw e;
      }
    }

    String[] fileSplits = fileNameNoExt.split(File.separator, 2);
    String ruleName = fileSplits[0];
    String testCaseName = fileSplits[1];

    String actualTree;
    try (InputStream efInputStream = new BufferedInputStream(url(fileNameNoExt + ".ef").openStream());
         Reader efReader = new InputStreamReader(efInputStream, Charsets.UTF_8)) {
      EffesParser parser = ParserUtils.createParser(efReader);
      parser.setTrace(Boolean.getBoolean("test.antlr.trace"));
      parser.addErrorListener(new ParserUtils.ExceptionThrowingFailureListener());
      if (testCaseName.startsWith("_")) {
        // signifies a fragment
        TokenSource lexer = parser.getTokenStream().getTokenSource();
        EffesLexer effesLexer = (EffesLexer) lexer;
        effesLexer.getDenterOptions().ignoreEOF();
      }

      try {
        RuleContext rule = ParserUtils.ruleByName(parser, ruleName);
        StringBuilder sb = new StringBuilder();
        ParserUtils.prettyPrint(sb, rule, parser);
        actualTree = sb.toString();
        literalsInvoker.invokeLiteralTokenMethods(rule);
      } catch (Exception e) {
        actualTree = String.format("!!! %s: %s", e.getClass().getSimpleName(), e.getMessage());
      }
    }
    if (Boolean.getBoolean("effesParserTest.write")) {
      File outDir = new File("target/effesParserTest/" + ruleName);
      outDir.mkdirs();
      try (FileWriter w = new FileWriter(new File(outDir, testCaseName + ".tree"))) {
        w.append(actualTree);
      }
    }
    assertEquals(actualTree, expectedContent);
  }

  private URL url(String fileName) {
    return Resources.getResource(pathBase + "/" + fileName);
  }

}

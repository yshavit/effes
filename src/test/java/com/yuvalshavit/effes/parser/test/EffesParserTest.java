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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.testng.Assert.assertEquals;

public final class EffesParserTest {
  private static final String pathBase = EffesParserTest.class.getPackage().getName().replace('.', '/');

  public static final String PARSE_TESTS = "test1";

  @DataProvider(name = "test1")
  public Object[][] readParseFiles() {
    Set<String> files = Sets.newTreeSet();
    Collections.addAll(files, readFile(".").split("\n"));
    List<Object[]> parseTests = Lists.newArrayList();
    for (String efFile : files) {
      if (efFile.endsWith(".ef")) {
        String plainFile = efFile.substring(0, efFile.length() - ".ef".length());
        parseTests.add(new Object[] { plainFile });
      }
    }
    return parseTests.toArray(new Object[parseTests.size()][]);
  }

  private String readFile(String relativePath) {
    try {
      return Resources.toString(url(relativePath), Charsets.UTF_8);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  @Test(dataProvider = PARSE_TESTS)
  public void parse(String fileNameNoExt) throws IOException, ReflectiveOperationException {
    assertThat("file name", fileNameNoExt, containsString("-"));
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

    String ruleName = fileNameNoExt.split("-", 2)[0];

    String actualTree;
    try (InputStream efInputStream = new BufferedInputStream(url(fileNameNoExt + ".ef").openStream());
         Reader efReader = new InputStreamReader(efInputStream, Charsets.UTF_8)) {
      EffesParser parser = ParserUtils.createParser(efReader);
      parser.setTrace(true);
      parser.addErrorListener(new ParserUtils.ExceptionThrowingFailureListener());
      if (ruleName.startsWith("_")) {
        // signifies a fragment
        ruleName = ruleName.substring(1);
        TokenSource lexer = parser.getTokenStream().getTokenSource();
        EffesLexer effesLexer = (EffesLexer) lexer;
        effesLexer.getDenterOptions().ignoreEOF();
      }

      RuleContext rule = ParserUtils.ruleByName(parser, ruleName);
      StringBuilder sb = new StringBuilder();
      ParserUtils.prettyPrint(sb, rule, parser);
      actualTree = sb.toString();
    }
    assertEquals(actualTree, expectedContent);
  }

  private URL url(String fileName) {
    return Resources.getResource(pathBase + "/" + fileName);
  }

}

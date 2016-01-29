package com.yuvalshavit.effes.test;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.yuvalshavit.effes.compile.Source;
import com.yuvalshavit.effes.compile.Sources;
import com.yuvalshavit.effes.compile.node.Block;
import com.yuvalshavit.effes.compile.node.BuiltInMethodsFactory;
import com.yuvalshavit.effes.compile.node.CompileErrors;
import com.yuvalshavit.effes.compile.node.EfMethod;
import com.yuvalshavit.effes.compile.IrCompiler;
import com.yuvalshavit.effes.compile.node.MethodId;
import com.yuvalshavit.effes.compile.MethodsRegistry;
import com.yuvalshavit.effes.compile.node.Node;
import com.yuvalshavit.effes.compile.NodeStateVisitor;
import com.yuvalshavit.effes.interpreter.ExecutableBuiltInMethods;
import com.yuvalshavit.effes.interpreter.Interpreter;
import com.yuvalshavit.effes.parser.ParserUtils;
import com.yuvalshavit.util.RelativeUrl;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;

public final class EndToEndTest {
  private static final RelativeUrl urls = new RelativeUrl(EndToEndTest.class);
  private final String efPrefix;

  public EndToEndTest() throws IOException {
    efPrefix = Resources.toString(urls.get("_prefix.ef"), Charsets.UTF_8);
  }

  @DataProvider(name = "files")
  public static Object[][] getBaseNames() throws IOException {
    String regexPattern = System.getProperty("test.regex", "");
    String suffixRegex = "^(.*" + regexPattern + ".*)\\.(ef|ir|err|out)$";
    return Resources.readLines(urls.get("."), Charsets.UTF_8).stream()
      .flatMap(EndToEndTest::readFileOrDir)
      .filter(s -> !"_prefix.ef".equals(s) && s.matches(suffixRegex))
      .map(s -> s.replaceFirst(suffixRegex, "$1"))
      .distinct()
      .filter(EndToEndTest::dataProviderFilter)
      .map(s -> new Object[]{s})
      .collect(Collectors.toList())
      .toArray(new Object[0][]);
  }

  private static boolean dataProviderFilter(String testName) {
    String testparam = System.getProperty("test.params");
    if (testparam == null) {
      return true;
    }
    Pattern pattern = Pattern.compile(testparam);
    return pattern.matcher(testName).find();
  }

  private static Stream<String> readFileOrDir(String file) {
    // This is hacky as hell, but Guava's ClassPath...getResource() is failing for me, and I don't feel like writing
    // my own version of it. So, just take the incoming file "foo" and see if we can read "foo/.". This will only work
    // for directories, and it'll throw NPE (not IOException) for files. So, yes, yucky... but gets the job done.
    try {
      return Resources.readLines(urls.get(file + "/."), Charsets.UTF_8).stream().map(f -> file + "/" + f);
    } catch (NullPointerException e) {
      return ImmutableList.of(file).stream();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  @Test(dataProvider = "files")
  public void compile(String fileBaseName) throws IOException {
    IrCompiler<?> compiler = new IrCompiler<>(
      getParser(fileBaseName),
      (t, e) -> {
        BuiltInMethodsFactory<?> factory = new ExecutableBuiltInMethods(t, null);
        MethodsRegistry<Object> reg = new MethodsRegistry<>();
        factory.addTo(t, reg, e);
        return reg;
      },
      new CompileErrors());

    String irFileName = fileBaseName + ".ir";
    String errFileName = fileBaseName + ".err";
    String actualIr = compiler.getErrors().hasErrors()
      ? ""
      : printIr(compiler.getCompiledMethods());
    assertEquals(Joiner.on('\n').join(compiler.getErrors().getErrors()), readIfExists(errFileName));
    assertEquals(actualIr, readIfExists(irFileName));
  }

  private Sources getParser(String fileBaseName) throws IOException {
    URL url = urls.get(fileBaseName + ".ef");
    String efFile = Resources.toString(url, Charsets.UTF_8);
    Source prefix = new Source(ParserUtils.createParser(efPrefix).compilationUnit());
    Source parser = new Source(ParserUtils.createParser(efFile).compilationUnit());
    return new Sources(Arrays.asList(prefix, parser));
  }

  @Test(dataProvider = "files")
  public void run(String fileBaseName) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(baos);
    Interpreter interpreter = new Interpreter(getParser(fileBaseName), out);

    String outFileName = fileBaseName + ".out";
    String errFileName = fileBaseName + ".err";
    String outStr;
    if (interpreter.hasErrors()) {
      outStr = "";
    } else {
      Object result = interpreter.runMain();
      out.print(">> ");
      out.println(result);
      out.flush();
      out.close();
      outStr = stripTrailingNewline(baos.toString("utf-8"));
    }

    assertEquals(Joiner.on('\n').join(interpreter.getErrors().getErrors()), readIfExists(errFileName));
    assertEquals(outStr, readIfExists(outFileName));
  }

  private String printIr(MethodsRegistry<Block> compiledMethods) {
    Map<? extends MethodId, ? extends EfMethod<? extends Block>> methods = compiledMethods.getMethodsByName();
    methods = new TreeMap<>(methods); // sort by name
    StringBuilder sb = new StringBuilder();
    NodeStateVisitor listener = new NodeStateToString(sb);
    methods.forEach((name, method) -> {
      if(!MethodId.topLevel("main").equals(name)) {
        sb.append("def ").append(name).append(' ').append(method).append(":\n");
        listener.visitChild(method.getBody());
      }
    });
    return stripTrailingNewline(sb.toString());
  }

  private String readIfExists(String fileName) throws IOException {
    URL url = urls.tryGet(fileName);
    return url == null
      ? ""
      : stripTrailingNewline(Resources.toString(url, Charsets.UTF_8));
  }

  private static String stripTrailingNewline(String string) {
    if (string.endsWith("\n")) {
      string = string.substring(0, string.length() - 1);
    }
    return string;
  }

  private static class NodeStateToString implements NodeStateVisitor {
    private final StringBuilder out;
    private int depth = 0;

    private NodeStateToString(StringBuilder out) {
      this.out = out;
    }

    @Override
    public void visitChild(Object label, Node child) {
      indent().append(label).append(":\n");
      ++depth;
      visitChild(child);
      --depth;
    }

    @Override
    public void visitChild(Node child) {
      if (child.elideFromState()) {
        child.state(this);
        return;
      }
      indent();
      out.append(child.getClass().getSimpleName()).append('\n');
      ++depth;
      child.state(this);
      --depth;
    }

    @Override
    public void visitScalar(Object label, Object value) {
      indent();
      if (label != null) {
        out.append(label).append(": ");
      }
      out.append(value).append('\n');
    }

    private StringBuilder indent() {
      for (int i = 0; i < depth + 1; ++i) {
        out.append("  ");
      }
      return out;
    }
  }
}

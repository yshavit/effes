package com.yuvalshavit.effes.test;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.yuvalshavit.effes.compile.Block;
import com.yuvalshavit.effes.compile.BuiltInMethodsFactory;
import com.yuvalshavit.effes.compile.CompileErrors;
import com.yuvalshavit.effes.compile.EfMethod;
import com.yuvalshavit.effes.compile.IrCompiler;
import com.yuvalshavit.effes.compile.MethodId;
import com.yuvalshavit.effes.compile.MethodsRegistry;
import com.yuvalshavit.effes.compile.Node;
import com.yuvalshavit.effes.compile.NodeStateListener;
import com.yuvalshavit.effes.interpreter.ExecutableBuiltInMethods;
import com.yuvalshavit.effes.interpreter.Interpreter;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;
import com.yuvalshavit.util.RelativeUrl;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
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
      .flatMap((Function<String, Stream<String>>)EndToEndTest::readFileOrDir) // IDEA-123366
      .filter(s -> !"_prefix.ef".equals(s) && s.matches(suffixRegex))
      .map(s -> s.replaceFirst(suffixRegex, "$1"))
      .distinct()
      .map(s -> new Object[]{s})
      .collect(Collectors.toList())
      .toArray(new Object[0][]);
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
      t -> {
        BuiltInMethodsFactory<?> factory = new ExecutableBuiltInMethods(t, null);
        MethodsRegistry<Object> reg = new MethodsRegistry<>();
        factory.addTo(t, reg);
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

  private EffesParser.CompilationUnitContext getParser(String fileBaseName) throws IOException {
    String efFile = Resources.toString(urls.get(fileBaseName + ".ef"), Charsets.UTF_8) + "\n\n" + efPrefix;
    EffesParser parser = ParserUtils.createParser(efFile);
    return parser.compilationUnit();
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
      outStr = baos.toString("utf-8");
    }

    assertEquals(Joiner.on('\n').join(interpreter.getErrors().getErrors()), readIfExists(errFileName));
    assertEquals(outStr, readIfExists(outFileName));
  }

  private String printIr(MethodsRegistry<Block> compiledMethods) {
    Map<? extends MethodId, ? extends EfMethod<? extends Block>> methods = compiledMethods.getTopLevelMethodsByName();
    methods = new TreeMap<>(methods); // sort by name
    StringBuilder sb = new StringBuilder();
    NodeStateListener listener = new NodeStateToString(sb);
    methods.forEach((name, method) -> {
      if(!MethodId.topLevel("main").equals(name)) {
        sb.append("def ").append(name).append(' ').append(method).append(":\n");
        listener.child(method.getBody());
      }
    });
    return sb.toString();
  }

  private String readIfExists(String fileName) throws IOException {
    URL url = urls.get(fileName);
    return url != null
      ? Resources.toString(url, Charsets.UTF_8)
      : "";
  }

  private static class NodeStateToString implements NodeStateListener {
    private final StringBuilder out;
    private int depth = 0;

    private NodeStateToString(StringBuilder out) {
      this.out = out;
    }

    @Override
    public void child(Object label, Node child) {
      indent().append(label).append(":\n");
      ++depth;
      child(child);
      --depth;
    }

    @Override
    public void child(Node child) {
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
    public void scalar(Object label, Object value) {
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

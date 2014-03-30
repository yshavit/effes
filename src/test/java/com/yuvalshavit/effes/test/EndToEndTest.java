package com.yuvalshavit.effes.test;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.Resources;
import com.yuvalshavit.effes.compile.Block;
import com.yuvalshavit.effes.compile.BuiltInMethodsFactory;
import com.yuvalshavit.effes.compile.CompileErrors;
import com.yuvalshavit.effes.compile.EfMethod;
import com.yuvalshavit.effes.compile.IrCompiler;
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
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;

public final class EndToEndTest {
  private static final RelativeUrl urls = new RelativeUrl(EndToEndTest.class);
  private final String efPrefix;

  public EndToEndTest() throws IOException {
    efPrefix = Resources.toString(urls.get("_prefix.ef"), Charsets.UTF_8);
  }

  @DataProvider(name = "files")
  public static Object[][] getBaseNames() throws IOException{
    String suffixRegex = "^(.*)\\.(ef|ir|err)$";
    return Resources.readLines(urls.get("."), Charsets.UTF_8).stream()
      .filter(s -> !"_prefix.ef".equals(s) && s.matches(suffixRegex))
      .map(s -> s.replaceFirst(suffixRegex, "$1"))
      .distinct()
      .map(s -> new Object[]{s})
      .collect(Collectors.toList())
      .toArray(new Object[0][]);
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
      ? null
      : printIr(compiler.getCompiledMethods());
    assertEquals(Joiner.on('\n').join(compiler.getErrors().getErrors()), readIfExists(errFileName));
    assertEquals(actualIr, readIfExists(irFileName));
  }

  private EffesParser.CompilationUnitContext getParser(String fileBaseName) throws IOException {
    String efFile = efPrefix + Resources.toString(urls.get(fileBaseName + ".ef"), Charsets.UTF_8);
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
      outStr = null;
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
    Map<? extends String, ? extends EfMethod<? extends Block>> methods = compiledMethods.getTopLevelMethodsByName();
    methods = new TreeMap<>(methods); // sort by name
    StringBuilder sb = new StringBuilder();
    NodeStateListener listener = new NodeStateToString(sb);
    methods.forEach((name, method) -> {
      if(!"main".equals(name)) {
        sb.append("def ").append(name).append(' ').append(method).append(":\n");
        method.getBody().statements().forEach(listener::child);
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
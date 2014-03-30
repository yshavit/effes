package com.yuvalshavit.effes.test;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.Resources;
import com.yuvalshavit.effes.compile.Block;
import com.yuvalshavit.effes.compile.CompileErrors;
import com.yuvalshavit.effes.compile.EfMethod;
import com.yuvalshavit.effes.compile.IrCompiler;
import com.yuvalshavit.effes.compile.MethodsRegistry;
import com.yuvalshavit.effes.compile.Node;
import com.yuvalshavit.effes.compile.NodeStateListener;
import com.yuvalshavit.effes.compile.TypeRegistry;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.effes.parser.ParserUtils;
import com.yuvalshavit.util.RelativeUrl;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;

public final class IrTest {
  private static final RelativeUrl urls = new RelativeUrl(IrTest.class);
  private final String efPrefix;

  public IrTest() throws IOException {
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
    String efFileName = fileBaseName + ".ef";
    String irFileName = fileBaseName + ".ir";
    String errFileName = fileBaseName + ".err";

    String efFile = efPrefix + Resources.toString(urls.get(efFileName), Charsets.UTF_8);
    EffesParser parser = ParserUtils.createParser(efFile);
    CompileErrors errs = new CompileErrors();
    IrCompiler compiler = new IrCompiler(
      parser.compilationUnit(),
      new TypeRegistry(errs),
      new MethodsRegistry<>(),
      errs);
    MethodsRegistry<Block> compiledMethods = compiler.getCompiledMethods();
    CompileErrors errors = compiler.getErrors();

    String errsFile = readIfExists(errFileName);
    String irFile = readIfExists(irFileName);

    String actualErrs = Joiner.on('\n').join(errors.getErrors());
    String actualir = printIr(compiledMethods);

    assertEquals(actualErrs, errsFile);
    assertEquals(actualir, irFile);
  }

  private String printIr(MethodsRegistry<Block> compiledMethods) {
    Map<? extends String, ? extends EfMethod<? extends Block>> methods = compiledMethods.getTopLevelMethodsByName();
    methods = new TreeMap<>(methods); // sort by name
    StringBuilder sb = new StringBuilder();
    NodeStateListener listener = new NodeStateToString(sb);
    methods.forEach((name, method) -> {
      sb.append("def ").append(name).append(' ').append(method).append(":\n");
      method.getBody().statements().forEach(listener::child);
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
      for (int i = 0; i < depth; ++i) {
        out.append("  ");
      }
      return out;
    }
  }
}

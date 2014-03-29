package com.yuvalshavit.effes.compile.irtest;

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
import com.yuvalshavit.effes.compile.NodeVisitor;
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
      .map(s -> new Object[]{s})
      .collect(Collectors.toList())
      .toArray(new Object[0][]);
  }

  @Test(dataProvider = "files")
  public void t(String fileBaseName) throws IOException {
    String efFileName = fileBaseName + ".ef";
    String irFileName = fileBaseName + ".ir";
    String errFileName = fileBaseName + ".err";

    String efFile = efPrefix + Resources.toString(urls.get(efFileName), Charsets.UTF_8);
    EffesParser parser = ParserUtils.createParser(efFile);
    IrCompiler<?> compiler = new IrCompiler<>(parser.compilationUnit(), t -> new BuiltInMethods());
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
    methods.forEach((name, method) -> {
      sb.append("def ").append(name).append(' ').append(method).append(":");

    });
    return sb.toString();
  }

  private String readIfExists(String fileName) throws IOException {
    URL url = urls.get(fileName);
    return url != null
      ? Resources.toString(url, Charsets.UTF_8)
      : "";
  }

  private static class BuiltInMethods implements BuiltInMethodsFactory<Node> {
    @Override
    public Node print() {
      return new BuiltInMethod("print");
    }
  }

  private static class BuiltInMethod extends Node {
    private final String name;
    BuiltInMethod(String name) {
      super(null);
      this.name = name;
    }

    @Override
    public String toString() {
      return String.format("built-in method '%s'", name);
    }

    @Override
    public void validate(CompileErrors errs) {
      // valid
    }
  }
}

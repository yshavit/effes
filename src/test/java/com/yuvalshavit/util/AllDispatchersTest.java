package com.yuvalshavit.util;

import com.google.common.reflect.ClassPath;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.testng.Assert.assertTrue;

public final class AllDispatchersTest {
  private static final String oncePerDispatcher = "check field usage";
  private static final String dispatchMaps = "check maps";

  @Test(dataProvider = oncePerDispatcher)
  public void isStatic(Field dispatcher) {
    assertTrue(Modifier.isStatic(dispatcher.getModifiers()), "field should be static");
  }

  @Test(dataProvider = oncePerDispatcher)
  public void isFinal(Field dispatcher) {
    assertTrue(Modifier.isFinal(dispatcher.getModifiers()), "field should be final");
  }

  @Test(dataProvider = dispatchMaps)
  public void isDispatched(Field dispatcherField, Class<?> subclassUnderTest) throws Exception {
    Dispatcher<?,?,?> dispatcher = (Dispatcher) dispatcherField.get(null);
    assertTrue(dispatcher.baseClass.isAssignableFrom(subclassUnderTest));
    //noinspection SuspiciousMethodCalls
    assertTrue(dispatcher.dispatches.containsKey(subclassUnderTest), subclassUnderTest.toString());
  }

  @DataProvider(name = oncePerDispatcher)
  public static Object[][] allDispatches() throws Exception {
    return dispatcherFields()
      .stream()
      .map(f -> new Object[] { f })
      .collect(Collectors.toList())
      .toArray(new Object[0][]);
  }

  @DataProvider(name = dispatchMaps)
  public static Object[][] allDispatchClasses() throws Exception {
    List<Object[]> cases = new ArrayList<>();
    for (Field dispatcherField : dispatcherFields()) {
      if (!Modifier.isStatic(dispatcherField.getModifiers())) {
        continue; // isStatic will catch this
      }
      dispatcherField.setAccessible(true);
      Dispatcher<?,?,?> dispatcher = (Dispatcher) dispatcherField.get(null);
      for(Class<?> subclass : findSubclasses(dispatcher.baseClass)) {
        cases.add(new Object[] { dispatcherField, subclass });
      }
    }
    return cases.toArray(new Object[cases.size()][]);
  }

  private static Collection<Field> dispatcherFields() throws IOException {
    Collection<Class<?>> topLevelClassInfos = ClassPath
      .from(AllDispatchersTest.class.getClassLoader())
      .getTopLevelClassesRecursive("com.yuvalshavit.effes")
      .stream()
      .map(info -> {
        try {
          return Class.forName(info.getName());
        } catch (ClassNotFoundException e) {
          throw new RuntimeException(e);
        }
      })
      .collect(Collectors.toSet());
    Collection<Field> dispatcherFields = new ArrayList<>();
    for (Class<?> cls : topLevelClassInfos) {
      findDispatchers(cls, dispatcherFields);
    }
    return dispatcherFields;
  }

  private static void findDispatchers(Class<?> cls, Collection<? super Field> result) {
    if (Dispatcher.class.equals(cls)) {
      return; // so we don't find Dispatcher fields within the Dispatcher itself!
    }
    for (Field field : cls.getDeclaredFields()) {
      if (Dispatcher.class.equals(field.getType())) {
        result.add(field);
      }
    }
    for (Class<?> nested : cls.getDeclaredClasses()) {
      findDispatchers(nested, result);
    }
  }

  private static Collection<Class<?>> findSubclasses(Class<?> baseClass) {
    Class<?> lookIn = baseClass;
    while (lookIn.getDeclaringClass() != null) {
      lookIn = lookIn.getDeclaringClass();
    }
    return Stream.of(lookIn.getClasses())
      .filter(baseClass::isAssignableFrom)
      .collect(Collectors.toList());
  }
}

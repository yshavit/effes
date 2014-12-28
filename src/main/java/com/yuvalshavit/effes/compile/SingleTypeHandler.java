/*
 * Copyright © 2014 Sqrrl Data, Inc. All Rights Reserved.
 *
 * You may not use, distribute or modify this code except
 * with the express written authorization of Sqrrl Data,
 * Inc. under the terms of the Sqrrl Enterprise license.
 */
package com.yuvalshavit.effes.compile;

import java.util.function.Consumer;
import java.util.function.Function;

import org.antlr.v4.runtime.Token;

import com.yuvalshavit.effes.compile.node.CompileErrors;
import com.yuvalshavit.effes.parser.EffesParser;
import com.yuvalshavit.util.Dispatcher;

abstract class SingleTypeHandler<T> implements Function<EffesParser.SingleTypeContext, T> {

  private static final Object failedDispatch = new Object();
  
  private final CompileErrors errs;
  private final T onErr;

  public SingleTypeHandler(CompileErrors errs, T onErr) {
    this.errs = errs;
    this.onErr = onErr;
  }

  @Override
  public T apply(EffesParser.SingleTypeContext ctx) {
    Object result = dispatcher.apply(this, ctx);
    if (failedDispatch.equals(result)) {
      return failedDispatch(ctx);
    } else {
      @SuppressWarnings("unchecked")
      T r = (T) result;
      return r;
    }
  }

  public static ConsumerBuilder0 consumer(CompileErrors errs) {
    return new ConsumerBuilderImpl(errs);
  }
  
  public static Consumer<EffesParser.SingleTypeContext> consume(CompileErrors errs, SingleTypeConsumer consumer) {
    class Adapter extends SingleTypeHandler<Void> implements Consumer<EffesParser.SingleTypeContext> {
      public Adapter() {
        super(errs, null);
      }

      @Override
      protected Void lookupDataType(EffesParser.SingleNonGenericContext ctx) {
        consumer.lookupDataType(ctx);
        return null;
      }

      @Override
      protected Void lookupGenericType(EffesParser.SingleGenericTypeContext ctx) {
        consumer.lookupGenericType(ctx);
        return null;
      }

      @Override
      public void accept(EffesParser.SingleTypeContext ctx) {
        apply(ctx);
      }
    }
    return new Adapter();
  }

  private static final Dispatcher<SingleTypeHandler,EffesParser.SingleTypeContext,Object> dispatcher =
    Dispatcher.builder(SingleTypeHandler.class, EffesParser.SingleTypeContext.class, Object.class)
      .put(EffesParser.SingleDataTypeContext.class, SingleTypeHandler::lookupDataType)
      .put(EffesParser.SingleGenericTypeContext.class, SingleTypeHandler::lookupGenericType)
      .build(SingleTypeHandler::failedDispatch);

  private T failedDispatch(EffesParser.SingleTypeContext ctx) {
    Token tok = ctx == null
      ? null
      : ctx.getStart();
    errs.add(tok, "couldn't figure out what type of type this is (this is a compiler bug)");
    return onErr;
  }

  protected abstract T lookupDataType(EffesParser.SingleNonGenericContext ctx);
  protected abstract T lookupGenericType(EffesParser.SingleGenericTypeContext ctx);
  
  private T lookupDataType(EffesParser.SingleDataTypeContext ctx) {
    return lookupDataType(ctx.singleNonGeneric());
  }

  public interface ConsumerBuilder0 {
    ConsumerBuilder1 onDataType(Consumer<EffesParser.SingleNonGenericContext> consumer);
  }

  public interface ConsumerBuilder1 {
    Consumer<EffesParser.SingleTypeContext> onGeneric(Consumer<EffesParser.SingleGenericTypeContext> consumer);
  }
  
  private static class ConsumerBuilderImpl extends SingleTypeConsumer implements ConsumerBuilder0, ConsumerBuilder1 {
    private final CompileErrors errs;
    private Consumer<EffesParser.SingleNonGenericContext> dataTypeConsumer;
    private Consumer<EffesParser.SingleGenericTypeContext> genericConsumer;

    public ConsumerBuilderImpl(CompileErrors errs) {
      this.errs = errs;
    }

    @Override
    protected void lookupDataType(EffesParser.SingleNonGenericContext ctx) {
      dataTypeConsumer.accept(ctx);
    }

    @Override
    protected void lookupGenericType(EffesParser.SingleGenericTypeContext ctx) {
      genericConsumer.accept(ctx);
    }

    @Override
    public ConsumerBuilder1 onDataType(Consumer<EffesParser.SingleNonGenericContext> consumer) {
      dataTypeConsumer = consumer;
      return this;
    }

    @Override
    public Consumer<EffesParser.SingleTypeContext> onGeneric(Consumer<EffesParser.SingleGenericTypeContext> consumer) {
      this.genericConsumer = consumer;
      return consume(errs, this);
    }
  }
  
  static abstract class SingleTypeConsumer {
    protected abstract void lookupDataType(EffesParser.SingleNonGenericContext ctx);
    protected abstract void lookupGenericType(EffesParser.SingleGenericTypeContext ctx);
  }
  
}

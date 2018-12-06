/*
 *
 *
 * Copyright 2018 Symphony Communication Services, LLC.
 *
 * Licensed to The Symphony Software Foundation (SSF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.symphonyoss.s2.fugue.pubsub;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.symphonyoss.s2.common.fault.FaultAccumulator;
import org.symphonyoss.s2.common.fault.TransientTransactionFault;
import org.symphonyoss.s2.fugue.FugueLifecycleState;
import org.symphonyoss.s2.fugue.config.IConfiguration;
import org.symphonyoss.s2.fugue.core.trace.ITraceContext;
import org.symphonyoss.s2.fugue.core.trace.ITraceContextTransactionFactory;
import org.symphonyoss.s2.fugue.counter.Counter;
import org.symphonyoss.s2.fugue.counter.ICounter;
import org.symphonyoss.s2.fugue.naming.TopicName;
import org.symphonyoss.s2.fugue.pipeline.FatalConsumerException;
import org.symphonyoss.s2.fugue.pipeline.IThreadSafeErrorConsumer;
import org.symphonyoss.s2.fugue.pipeline.IThreadSafeRetryableConsumer;
import org.symphonyoss.s2.fugue.pipeline.RetryableConsumerException;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * Base class for subscriber managers.
 * 
 * @author Bruce Skingle
 *
 * @param <P> Type of payload received.
 * @param <T> Type of concrete manager, needed for fluent methods.
 */
public abstract class AbstractSubscriberManager<P, T extends AbstractSubscriberManager<P,T>> extends AbstractSubscriberBase<P,T>
implements ISubscriberManager<P, T>
{
  protected static final long          FAILED_DEAD_LETTER_RETRY_TIME = TimeUnit.HOURS.toMillis(1);
  protected static final long          FAILED_CONSUMER_RETRY_TIME    = TimeUnit.SECONDS.toMillis(30);
  protected static final long          MESSAGE_PROCESSED_OK          = -1;

  private static final Logger                     log_                          = LoggerFactory.getLogger(AbstractSubscriberManager.class);
  private static final Integer                    FAILURE_CNT_LIMIT             = 5;

  protected final ITraceContextTransactionFactory traceFactory_;
  protected final IThreadSafeErrorConsumer<P>     unprocessableMessageConsumer_;
  protected final IConfiguration                  config_;
  protected final ICounter                        counter_;

  // TODO: replace this with a local topic
  private Cache<String, Integer>            failureCache_                 = CacheBuilder.newBuilder()
                                                                            .maximumSize(5000)
                                                                            .expireAfterAccess(30, TimeUnit.MINUTES)
                                                                            .build();
  

  protected AbstractSubscriberManager(Class<T> type, Builder<P,?,T> builder)
  {
    super(type, builder);
    
    traceFactory_                 = builder.traceFactory_;
    unprocessableMessageConsumer_ = builder.unprocessableMessageConsumer_;
    config_                       = builder.config_;
    counter_                      = builder.counter_;
  }

  /**
   * Builder.
   * 
   * @author Bruce Skingle
   *
   * @param <T>   The concrete type returned by fluent methods.
   * @param <B>   The concrete type of the built object.
   */
  protected static abstract class Builder<P, T extends Builder<P,T,B>, B extends AbstractSubscriberManager<P,B>>
  extends AbstractSubscriberBase.Builder<P,T,B>
  implements ISubscriberManagerBuilder<P,T,B>
  {
    private ITraceContextTransactionFactory traceFactory_;
    private IThreadSafeErrorConsumer<P>     unprocessableMessageConsumer_;
    private IConfiguration                  config_;
    private ICounter                        counter_;

    protected Builder(Class<T> type)
    {
      super(type);
    }

    @Override
    public T withSubscription(IThreadSafeRetryableConsumer<P> consumer, Subscription subscription)
    {
      return super.withSubscription(consumer, subscription);
    }
  
    @Override
    public T withSubscription(IThreadSafeRetryableConsumer<P> consumer, String subscriptionId, String topicId,
        String... additionalTopicIds)
    {
      return super.withSubscription(consumer, subscriptionId, topicId, additionalTopicIds);
    }
  
    @Override
    public T withSubscription(IThreadSafeRetryableConsumer<P> consumer, String subscriptionId, Collection<TopicName> topicNames)
    {
      return super.withSubscription(consumer, subscriptionId, topicNames);
    }
    
    @Override
    public T withSubscription(IThreadSafeRetryableConsumer<P> consumer, String subscriptionId, String[] topicNames)
    {
      return super.withSubscription(consumer, subscriptionId, topicNames);
    }

    @Override
    public T withConfig(IConfiguration config)
    {
      config_ = config;
      
      return self();
    }

    @Override
    public T withCounter(ICounter counter)
    {
      counter_ = counter;
      
      return self();
    }

    @Override
    public T withTraceContextTransactionFactory(ITraceContextTransactionFactory traceFactory)
    {
      traceFactory_ = traceFactory;
      
      return self();
    }

    @Override
    public T withUnprocessableMessageConsumer(IThreadSafeErrorConsumer<P> unprocessableMessageConsumer)
    {
      unprocessableMessageConsumer_ = unprocessableMessageConsumer;
      
      return self();
    }

    @Override
    public void validate(FaultAccumulator faultAccumulator)
    {
      super.validate(faultAccumulator);
      
      faultAccumulator.checkNotNull(traceFactory_, "traceFactory");
      faultAccumulator.checkNotNull(unprocessableMessageConsumer_, "unprocessableMessageConsumer");
      faultAccumulator.checkNotNull(config_, "config");
    }
  }
  
  protected ICounter getCounter()
  {
    return counter_;
  }
  
  protected abstract void initSubscription(SubscriptionImpl<P> subscription);
  protected abstract void startSubscriptions();
  /**
   * Stop all subscribers.
   */
  protected abstract void stopSubscriptions();
  
  protected ITraceContextTransactionFactory getTraceFactory()
  {
    return traceFactory_;
  }

  @Override
  public synchronized void start()
  {
    setLifeCycleState(FugueLifecycleState.Starting);
    
    for(SubscriptionImpl<P> s : getSubscribers())
    {
      initSubscription(s);
    }
    
    startSubscriptions();
    
    setLifeCycleState(FugueLifecycleState.Running);
  }

  @Override
  public void quiesce()
  {
    setLifeCycleState(FugueLifecycleState.Quiescing);
    
    stopSubscriptions();
    
    setLifeCycleState(FugueLifecycleState.Quiescent);
  }

  @Override
  public synchronized void stop()
  {
    setLifeCycleState(FugueLifecycleState.Stopping);
    
    stopSubscriptions();
    
    setLifeCycleState(FugueLifecycleState.Stopped);
  }

  /**
   * Handle the given message.
   * 
   * @param consumer  The consumer for the message.
   * @param payload   A received message.
   * @param trace     A trace context.
   * @param messageId A unique ID for the message.
   * 
   * @return The number of milliseconds after which a retry should be made, or -1 if the message was
   * processed and no retry is necessary.
   */
  public long handleMessage(IThreadSafeRetryableConsumer<P> consumer, P payload, ITraceContext trace, String messageId)
  {
    try
    {
      consumer.consume(payload, trace);
    }
    catch (TransientTransactionFault e)
    {
      log_.warn("TransientTransactionFault, will retry (forever)", e);
      return retryMessage(payload, trace, e, messageId, FAILED_CONSUMER_RETRY_TIME, true);
    }
    catch (RetryableConsumerException e)
    {
      log_.warn("Unprocessable message, will retry", e);
      
      if(e.getRetryTime() == null || e.getRetryTimeUnit() == null)
        return retryMessage(payload, trace, e, messageId, FAILED_CONSUMER_RETRY_TIME, false);
      
      return retryMessage(payload, trace, e, messageId, e.getRetryTimeUnit().toMillis(e.getRetryTime()), false);
    }
    catch (RuntimeException  e)
    {
      return retryMessage(payload, trace, e, messageId, FAILED_CONSUMER_RETRY_TIME, false);
    }
    catch (FatalConsumerException e)
    {
      log_.error("Unprocessable message, aborted", e);

      trace.trace("MESSAGE_IS_UNPROCESSABLE");
      
      return abortMessage(payload, trace, e);
    }
    
    return MESSAGE_PROCESSED_OK;
  }

  private long retryMessage(P payload, ITraceContext trace, Throwable cause, String messageId, long retryTime, boolean retryForever)
  {
    if(!retryForever)
    {
      Integer cnt = failureCache_.getIfPresent(messageId);
      
      if(cnt == null)
      {
        cnt = 1;
        failureCache_.put(messageId, cnt);
      }
      else
      {
        if(cnt >= FAILURE_CNT_LIMIT)
        {
          trace.trace("MESSAGE_RETRIES_EXCEEDED");
          failureCache_.invalidate(messageId);
          return abortMessage(payload, trace, cause);
        }
        failureCache_.put(messageId, ++cnt);
      }
      log_.warn("Message processing failed " + cnt + " times, will retry", cause);
    }
    
    return retryTime;
  }

  private long abortMessage(P payload, ITraceContext trace, Throwable e)
  {
    try
    {
      unprocessableMessageConsumer_.consume(payload, trace, e.getLocalizedMessage(), e);
      return MESSAGE_PROCESSED_OK;
    }
    catch(RuntimeException e2)
    {
      log_.error("Unprocessable message consumer failed", e);
      return FAILED_DEAD_LETTER_RETRY_TIME;
    }
  }
}

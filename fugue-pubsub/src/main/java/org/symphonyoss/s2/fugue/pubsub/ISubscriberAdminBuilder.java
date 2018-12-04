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

import org.symphonyoss.s2.common.fluent.IFluentBuilder;
import org.symphonyoss.s2.fugue.naming.TopicName;

/**
 * A builder for a subscriber admin controller.
 * 
 * @author Bruce Skingle
 *
 * @param <T> Type of concrete builder, needed for fluent methods.
 * @param <B> Type of concrete manager (built object), needed for fluent methods.
 */
public interface ISubscriberAdminBuilder<T extends ISubscriberAdminBuilder<T,B>, B extends ISubscriberAdmin<B>> extends IFluentBuilder<T,B>
{
  T withSubscription(Subscription subscription);
  
  /**
   * Subscribe to the given subscription on the given topics.
   * 
   * This method allows for the creation of the same subscription on one or more topics, the same consumer will receive 
   * messages received on the given subscription on any of the topics. The topics are all treated in the same way, the
   * method is declared with topic and additionalTopics to ensure that at least one topic is provided.
   * 
   * This method does the same thing as the other withSubscription methods, alternative signatures are provided as a convenience.
   * 
   * @param subscriptionName        A subscription name.
   * @param topicName               A topic name.
   * @param additionalTopicNames    An optional list of additional topic names.
   * 
   * @return  this (fluent method)
   */
  T withSubscription(String subscriptionName, String topicName, String ...additionalTopicNames);
  
  /**
   * Subscribe to the given subscription on the given topics.
   * 
   * This method allows for the creation of the same subscription on one or more topics, the same consumer will receive 
   * messages received on the given subscription on any of the topics. The topics are all treated in the same way, the
   * method is declared with topic and additionalTopics to ensure that at least one topic is provided.
   * 
   * This method does the same thing as the other withSubscription methods, alternative signatures are provided as a convenience.
   * 
   * @param subscriptionName        A subscription name.
   * @param topicNames              A list of topic names.
   * 
   * @return  this (fluent method)
   * 
   * @throws IllegalArgumentException If the list of topics is empty.
   */
  T withSubscription(String subscriptionName, Collection<TopicName> topicNames);

  /**
   * Subscribe to the given subscription on the given topics.
   * 
   * This method allows for the creation of the same subscription on one or more topics, the same consumer will receive 
   * messages received on the given subscription on any of the topics. The topics are all treated in the same way, the
   * method is declared with topic and additionalTopics to ensure that at least one topic is provided.
   * 
   * This method does the same thing as the other withSubscription methods, alternative signatures are provided as a convenience.
   * 
   * @param subscriptionName        A subscription name.
   * @param topicNames              A list of topic names.
   * 
   * @return  this (fluent method)
   * 
   * @throws IllegalArgumentException If the list of topics is empty.
   */
  T withSubscription(String subscriptionName, String[] topicNames);
}
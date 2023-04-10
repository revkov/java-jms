/*
 * Copyright 2017-2020 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.opentracing.contrib.jms.common;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.jms.client.ActiveMQQueue;
import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;



public class TracingMessageUtilsTest {

  private final MockTracer mockTracer = new MockTracer();
  private static ClientSession clientSession;

  //private EmbeddedActiveMQ embeddedActiveMQ;

  @BeforeAll
  public static void beforeAll() throws Exception {
    if (clientSession == null) {
      Configuration config = new ConfigurationImpl();
      config.addAcceptorConfiguration("in-vm", "vm://0");
      config.setSecurityEnabled(false);
      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfiguration(config);
      embeddedActiveMQ.start();
      ServerLocator locator = ActiveMQClient.createServerLocator("vm://0");
      ClientSessionFactory factory =  locator.createSessionFactory();
      clientSession = factory.createSession();
    }
  }

  @BeforeEach
  public void before() throws Exception {
    mockTracer.reset();
  }

  @Test
  public void noSpanToExtract() throws Exception {
    SpanContext context = TracingMessageUtils.extract(new ActiveMQTextMessage(clientSession), mockTracer);
    //clientSession.stop();
    //embeddedActiveMQ.stop();
    Assertions.assertNull(context);
  }

  @Test
  public void extractContextFromManager() throws Exception {
    MockSpan span = mockTracer.buildSpan("test").start();
    mockTracer.scopeManager().activate(span);
    MockSpan.MockContext context =
        (MockSpan.MockContext) TracingMessageUtils.extract(new ActiveMQTextMessage(clientSession), mockTracer);
    //clientSession.stop();
    //embeddedActiveMQ.stop();
    Assertions.assertNotNull(context);
    Assertions.assertEquals(span.context().spanId(), context.spanId());
  }

  @Test
  public void extractContextFromMessage() throws Exception {
    MockSpan span = mockTracer.buildSpan("test").start();
    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    TracingMessageUtils.inject(span, message, mockTracer);
    MockSpan.MockContext context =
        (MockSpan.MockContext) TracingMessageUtils.extract(message, mockTracer);
    //clientSession.stop();
    //embeddedActiveMQ.stop();
    Assertions.assertNotNull(context);
    Assertions.assertEquals(span.context().spanId(), context.spanId());
  }

  @Test
  public void startAndFinishConsumerSpan() throws Exception {
    MockSpan span = mockTracer.buildSpan("test").start();
    mockTracer.scopeManager().activate(span);
    SpanContext childContext =
        TracingMessageUtils.startAndFinishConsumerSpan(new ActiveMQTextMessage(clientSession), mockTracer);
    Assertions.assertNotNull(childContext);

    Assertions.assertNotNull(mockTracer.activeSpan());

    Assertions.assertEquals(1, mockTracer.finishedSpans().size());

    MockSpan finished = mockTracer.finishedSpans().get(0);
    //clientSession.stop();
    //embeddedActiveMQ.stop();
    Assertions.assertEquals(TracingMessageUtils.OPERATION_NAME_RECEIVE, finished.operationName());
    Assertions.assertEquals(Tags.SPAN_KIND_CONSUMER, finished.tags().get(Tags.SPAN_KIND.getKey()));
    Assertions.assertEquals(TracingMessageUtils.COMPONENT_NAME, finished.tags().get(Tags.COMPONENT.getKey()));
    Assertions.assertEquals(span.context().spanId(), finished.parentId());
    Assertions.assertEquals(span.context().traceId(), finished.context().traceId());
  }

  @Test
  public void inject() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    //clientSession.stop();
   // embeddedActiveMQ.stop();
    Assertions.assertTrue(message.getCoreMessage().getPropertyNames().isEmpty());

    MockSpan span = mockTracer.buildSpan("test").start();
    TracingMessageUtils.inject(span, message, mockTracer);
    Assertions.assertFalse(message.getCoreMessage().getPropertyNames().isEmpty());
  }

  @Test
  public void startAndInjectSpan() throws Exception {
    Destination destination = new ActiveMQQueue("queue");

    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    MockSpan span = mockTracer.buildSpan("test").start();
    mockTracer.scopeManager().activate(span);

    MockSpan injected =
        (MockSpan) TracingMessageUtils.startAndInjectSpan(destination, message, mockTracer);

    //clientSession.stop();
    //embeddedActiveMQ.stop();
    Assertions.assertFalse(message.getCoreMessage().getPropertyNames().isEmpty());
    Assertions.assertEquals(span.context().spanId(), injected.parentId());
  }

  @Test
  public void startListenerSpanWithoutParent() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    Span span = TracingMessageUtils.startListenerSpan(message, mockTracer);
    //clientSession.stop();
    //embeddedActiveMQ.stop();
    Assertions.assertTrue(span instanceof MockSpan);
  }

  @Test
  public void startListenerSpan() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    Destination destination = new ActiveMQQueue("queue");
    MockSpan injected =
        (MockSpan) TracingMessageUtils.startAndInjectSpan(destination, message, mockTracer);
    MockSpan span = (MockSpan) TracingMessageUtils.startListenerSpan(message, mockTracer);
    //clientSession.stop();
    //embeddedActiveMQ.stop();
    Assertions.assertEquals(span.parentId(), injected.context().spanId());
  }

  @Test
  public void startListenerSpanWithActiveSpan() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    MockSpan parent = mockTracer.buildSpan("test").start();
    mockTracer.scopeManager().activate(parent);
    MockSpan span = (MockSpan) TracingMessageUtils.startListenerSpan(message, mockTracer);
    //clientSession.stop();
    //embeddedActiveMQ.stop();
    Assertions.assertEquals(span.parentId(), parent.context().spanId());
  }

}

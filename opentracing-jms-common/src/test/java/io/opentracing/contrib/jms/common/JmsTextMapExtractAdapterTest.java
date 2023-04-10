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

import jakarta.jms.JMSException;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.junit.Rule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;
import org.junit.rules.ExpectedException;

import java.util.Iterator;
import java.util.Map;

import static io.opentracing.contrib.jms.common.JmsTextMapInjectAdapter.DASH;


@EnableRuleMigrationSupport
public class JmsTextMapExtractAdapterTest {

  private ActiveMQTextMessage message;

  private static ClientSession clientSession;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @BeforeAll
  public static void beforeAll() throws Exception {
    Configuration config = new ConfigurationImpl();

    config.addAcceptorConfiguration("in-vm", "vm://0");
    config.setSecurityEnabled(false);
    EmbeddedActiveMQ embedded = new EmbeddedActiveMQ();
    embedded.setConfiguration(config);
    embedded.start();
    ServerLocator locator = ActiveMQClient.createServerLocator("vm://0");
    ClientSessionFactory factory =  locator.createSessionFactory();
    clientSession = factory.createSession();

  }

  @BeforeEach
  public void  before() {
    message = new ActiveMQTextMessage(clientSession);
  }

  @Test
  public void cannotPut() {
    JmsTextMapExtractAdapter adapter = new JmsTextMapExtractAdapter(message);
    thrown.expect(UnsupportedOperationException.class);
    adapter.put("one", "two");
  }

  @Test
  public void noProperties() {
    JmsTextMapExtractAdapter adapter = new JmsTextMapExtractAdapter(message);
    Iterator<Map.Entry<String, String>> iterator = adapter.iterator();
    Assertions.assertFalse(iterator.hasNext());
  }

  @Test
  public void oneProperty() throws JMSException {
    message.setStringProperty("key", "value");
    JmsTextMapExtractAdapter adapter = new JmsTextMapExtractAdapter(message);
    Iterator<Map.Entry<String, String>> iterator = adapter.iterator();
    Map.Entry<String, String> entry = iterator.next();
    Assertions.assertEquals("key", entry.getKey());
    Assertions.assertEquals("value", entry.getValue());
  }

  @Test
  public void propertyWithDash() throws JMSException {
    message.setStringProperty(DASH + "key" + DASH + "1" + DASH, "value1");
    JmsTextMapExtractAdapter adapter = new JmsTextMapExtractAdapter(message);
    Iterator<Map.Entry<String, String>> iterator = adapter.iterator();
    Map.Entry<String, String> entry = iterator.next();
    Assertions.assertEquals("-key-1-", entry.getKey());
    Assertions.assertEquals("value1", entry.getValue());
  }

}

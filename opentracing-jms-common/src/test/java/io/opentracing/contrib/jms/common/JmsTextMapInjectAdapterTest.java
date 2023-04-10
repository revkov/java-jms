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
import org.junit.Before;
import org.junit.Rule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;
import org.junit.rules.ExpectedException;

import static io.opentracing.contrib.jms.common.JmsTextMapInjectAdapter.DASH;
import static org.junit.Assert.assertEquals;


@EnableRuleMigrationSupport
public class JmsTextMapInjectAdapterTest {

  private static ActiveMQTextMessage message;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @BeforeAll
  public static void before() throws Exception {
    Configuration config = new ConfigurationImpl();

    config.addAcceptorConfiguration("in-vm", "vm://0");
    config.setSecurityEnabled(false);
    EmbeddedActiveMQ embedded = new EmbeddedActiveMQ();
    embedded.setConfiguration(config);
    embedded.start();
    ServerLocator locator = ActiveMQClient.createServerLocator("vm://0");
    ClientSessionFactory factory =  locator.createSessionFactory();
    ClientSession session = factory.createSession();
    message = new ActiveMQTextMessage(session);
  }

  @Test
  public void cannotGetIterator() {
    JmsTextMapInjectAdapter adapter = new JmsTextMapInjectAdapter(message);
    thrown.expect(UnsupportedOperationException.class);
    adapter.iterator();
  }

  @Test
  public void putProperties() throws JMSException {
    JmsTextMapInjectAdapter adapter = new JmsTextMapInjectAdapter(message);
    adapter.put("key1", "value1");
    adapter.put("key2", "value2");
    adapter.put("key1", "value3");
    Assertions.assertEquals("value3", message.getStringProperty("key1"));
    Assertions.assertEquals("value2", message.getStringProperty("key2"));
  }

  @Test
  public void propertyWithDash() throws JMSException {
    JmsTextMapInjectAdapter adapter = new JmsTextMapInjectAdapter(message);
    adapter.put("key-1", "value1");
    Assertions.assertEquals("value1", message.getStringProperty("key" + DASH + "1"));

    adapter.put("-key-1-2-", "value2");
    Assertions.assertEquals("value2",
        message.getStringProperty(DASH + "key" + DASH + "1" + DASH + "2" + DASH));
  }
}

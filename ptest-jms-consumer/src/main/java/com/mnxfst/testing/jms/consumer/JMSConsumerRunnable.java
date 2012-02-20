/*
 *  ptest-server and client provides you with a performance test utility
 *  Copyright (C) 2012  Christian Kreutzfeldt <mnxfst@googlemail.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.mnxfst.testing.jms.consumer;

import java.util.Hashtable;
import java.util.concurrent.Callable;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.log4j.Logger;

/**
 * Provides a running context which 
 * @author mnxfst
 *
 */
public class JMSConsumerRunnable implements Callable<String>, MessageListener {

	private static final Logger logger = Logger.getLogger(JMSConsumerRunnable.class.getName());

	private long messagesReceived = 0;
	private long start = -1;
	private long end = -1;
	
	private String identifier = null;
	private ConnectionFactory connectionFactory = null;
	private Connection connection = null;
	private Session session = null;
	private Destination destination = null;
	private MessageConsumer messageConsumer = null;
		
	public JMSConsumerRunnable(Hashtable<String, String> jndiEnvironment, String connectionFactoryName, String jmsDestination) throws NamingException, JMSException {
		
//		Hashtable<String, String> jndiEnvironment = new Hashtable<String, String>();
//		jndiEnvironment.put(Context.INITIAL_CONTEXT_FACTORY, initialContextFactory);
//		jndiEnvironment.put(Context.PROVIDER_URL, brokerUrl);
//		if(securityCredentials != null && !securityCredentials.isEmpty())
//			jndiEnvironment.put(Context.SECURITY_CREDENTIALS, securityCredentials);
//		if(securityPrincipal != null && !securityPrincipal.isEmpty())
//			jndiEnvironment.put(Context.SECURITY_PRINCIPAL, securityPrincipal);
//		jndiEnvironment.put("topic.espTopic", "espTopic");
		
		InitialContext ctx = new InitialContext(jndiEnvironment);
		connectionFactory = (ConnectionFactory)ctx.lookup(connectionFactoryName);
		connection = connectionFactory.createConnection();
		session = connection.createSession(false,  Session.AUTO_ACKNOWLEDGE);
		destination = (Destination)ctx.lookup(jmsDestination);
		messageConsumer = session.createConsumer(destination);
		messageConsumer.setMessageListener(this);
		connection.start();
		identifier = new com.eaio.uuid.UUID().toString();
		logger.info("jmsConsumerRunnable[identifier="+identifier+"] started");
		start = System.currentTimeMillis();
	}
	
	public String call() {
		return identifier;
	}

	/**
	 * @see javax.jms.MessageListener#onMessage(javax.jms.Message)
	 */
	public void onMessage(Message msg) {
		System.out.println(msg);
		messagesReceived = messagesReceived + 1;
	}
	
	public void stop() throws JMSException {
		connection.stop();
		end = System.currentTimeMillis();
		logger.info("jmsConsumerRunnable[identifier="+identifier+"] stopped");
	}

	/**
	 * @return the messagesReceived
	 */
	public long getMessagesReceived() {
		return messagesReceived;
	}

	/**
	 * @return the start
	 */
	public long getStart() {
		return start;
	}

	/**
	 * @return the end
	 */
	public long getEnd() {
		return end;
	}
	
	public long getUpTime() {
		return System.currentTimeMillis() - start;
	}

}

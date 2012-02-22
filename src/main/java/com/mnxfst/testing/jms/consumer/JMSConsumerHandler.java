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

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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

import com.mnxfst.testing.consumer.exception.HttpRequestProcessingException;
import com.mnxfst.testing.consumer.handler.HttpRequestHandlerStatistics;
import com.mnxfst.testing.consumer.handler.IHttpRequestHandler;

/**
 * Implements a simple JMS destination consumer 
 * @author mnxfst
 *
 */
public class JMSConsumerHandler implements IHttpRequestHandler, MessageListener {

	private static final Logger logger = Logger.getLogger(JMSConsumerHandler.class.getName());
	
	private static final String REQUEST_PARAMETER_INITIAL_CONTEXT_FACTORY = "initialCtxFactory";
	private static final String REQUEST_PARAMETER_CONNECTION_FACTORY_NAME = "connectionFactoryName";
	private static final String REQUEST_PARAMETER_JMS_DESTINATION = "destination";
	private static final String REQUEST_PARAMETER_PROVIDER_URL = "providerUrl";
	private static final String REQUEST_PARAMETER_SECURITY_PRINCIPAL = "secPrincipal";
	private static final String REQUEST_PARAMETER_SECURITY_CREDENTIALS = "secCredentials";
	
	private static final String REQUEST_PARAMETER_VENDOR_SPECIFC_PREFIX = "vendor-";
	
	private String id = null;
	private String type = null;

	private ConnectionFactory connectionFactory = null;
	private Connection connection = null;
	private Session session = null;
	private Destination destination = null;
	private MessageConsumer messageConsumer = null;
	
	private int messagesReceived = 0;

	/**
	 * @see com.mnxfst.testing.consumer.handler.IHttpRequestHandler#initialize(java.util.Properties, java.util.Map)
	 */
	public void initialize(Properties configuration, Map<String, List<String>> queryParameters) throws HttpRequestProcessingException {

		// the JMS consumer receives configuration options just from the query parameters
		
		Hashtable<String, String> jndiEnvironment =  new Hashtable<String, String>();
		
		String initialContextFactoryClass = extractSingleString(REQUEST_PARAMETER_INITIAL_CONTEXT_FACTORY, queryParameters);
		if(initialContextFactoryClass == null || initialContextFactoryClass.isEmpty())
			throw new HttpRequestProcessingException("Missing required initial context factory class");
		
		String connectionFactoryName = extractSingleString(REQUEST_PARAMETER_CONNECTION_FACTORY_NAME, queryParameters);
		if(connectionFactoryName == null || connectionFactoryName.isEmpty())
			throw new HttpRequestProcessingException("Missing required connection factory lookup name");
		
		String providerUrl = extractSingleString(REQUEST_PARAMETER_PROVIDER_URL, queryParameters);
		if(providerUrl == null || providerUrl.isEmpty())
			throw new HttpRequestProcessingException("Missing required provider url");
		
		String jmsDestination = extractSingleString(REQUEST_PARAMETER_JMS_DESTINATION, queryParameters);
		if(jmsDestination == null || jmsDestination.isEmpty())
			throw new HttpRequestProcessingException("Missing required JMS destination lookup name");
		
		String securityPrincipal = extractSingleString(REQUEST_PARAMETER_SECURITY_PRINCIPAL, queryParameters);
		String securityCredentials = extractSingleString(REQUEST_PARAMETER_SECURITY_CREDENTIALS, queryParameters);
		
		jndiEnvironment.put(Context.INITIAL_CONTEXT_FACTORY, initialContextFactoryClass);
		jndiEnvironment.put(Context.PROVIDER_URL, providerUrl);
		if(securityCredentials != null && !securityCredentials.isEmpty())
			jndiEnvironment.put(Context.SECURITY_CREDENTIALS, securityCredentials);
		if(securityPrincipal != null && !securityPrincipal.isEmpty())
			jndiEnvironment.put(Context.SECURITY_PRINCIPAL, securityPrincipal);
		
		jndiEnvironment.putAll(extractVendorSpecificValues(queryParameters));

		try {
			// create initial context from collected settings
			InitialContext ctx = new InitialContext(jndiEnvironment);
			
			// lookup connection factory, create a connection and create a new session
			connectionFactory = (ConnectionFactory)ctx.lookup(connectionFactoryName);
			connection = connectionFactory.createConnection();
			try {
				connection.setClientID(id + "@"+InetAddress.getLocalHost().getHostName());
			} catch(Exception e) {
				logger.error("jmsConsumer[id="+this.id+", type="+this.type+"]: host name lookup failed. Client id will not be set for JMS connection. Error: " + e.getMessage());
			}
			session = connection.createSession(false,  Session.AUTO_ACKNOWLEDGE);
			
			// find desired destination and create a message consumer
			destination = (Destination)ctx.lookup(jmsDestination);
			messageConsumer = session.createConsumer(destination);
			messageConsumer.setMessageListener(this);
			
			// start listening
			connection.start();
			
		} catch(NamingException e) {
			throw new HttpRequestProcessingException("Failed to initialize naming context, lookup required objects and establish a connection. Error: " + e.getMessage());
		} catch (JMSException e) {
			throw new HttpRequestProcessingException("Failed to initialize naming context, lookup required objects and establish a connection. Error: " + e.getMessage());
		}

		logger.info("jmsConsumer[id="+this.id+", type="+this.type+", providerUrl="+providerUrl+", jmsDestination="+destination+", initialCtxFactory="+initialContextFactoryClass+", connectionFactoryName="+connectionFactoryName+"]");
		
	}


	/**
	 * @see javax.jms.MessageListener#onMessage(javax.jms.Message)
	 */
	public void onMessage(Message msg) {
		System.out.println(msg);
		messagesReceived = messagesReceived + 1;
		
		if(messagesReceived % 100 == 0) {
			logger.info("Received " + messagesReceived+ " messages");
			System.out.println("Received " + messagesReceived+ " messages");
		}
			
	}


	public void run() {

	}

	/**
	 * @see com.mnxfst.testing.consumer.handler.IHttpRequestHandler#shutdown()
	 */
	public void shutdown() throws HttpRequestProcessingException {
		try {
			connection.stop();
			session.close();
		} catch (JMSException e) {
			throw new HttpRequestProcessingException("Failed to shutdown " + JMSConsumerHandler.class.getName() + " (id="+id+") properly. Error: " + e.getMessage());
		}
		
	}

	public HttpRequestHandlerStatistics getHandlerStatistics() {
		return new HttpRequestHandlerStatistics();
	}

	/**
	 * Extracts a single value for the parameter referenced
	 * @param values
	 * @return
	 */
	protected String extractSingleString(String parameter, Map<String, List<String>> queryParams) {		
		List<String> values = queryParams.get(parameter);
		return (values != null && !values.isEmpty()) ? values.get(0) : null;		
	}

	/**
	 * Iterates through parameters, checks if each is prefixed with the vendor specific property key and extracts
	 * the value if necessary
	 * @param queryParams
	 * @return
	 */
	protected Map<String, String> extractVendorSpecificValues(Map<String, List<String>> queryParams) {
		
		Map<String, String> vendorSpecificSettings = new HashMap<String, String>();
		
		for(String parameter : queryParams.keySet()) {
			if(parameter.startsWith(REQUEST_PARAMETER_VENDOR_SPECIFC_PREFIX)) {
				String value = extractSingleString(parameter, queryParams);
				if(value != null) {
					vendorSpecificSettings.put(parameter.substring(REQUEST_PARAMETER_VENDOR_SPECIFC_PREFIX.length()), value);
				}
			}
		}
		
		return vendorSpecificSettings;
	}

	
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
}

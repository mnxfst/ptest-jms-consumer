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

package com.mnxfst.testing.consumer.jms;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.TopicSubscriber;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.log4j.Logger;

import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.SingleThreadedClaimStrategy;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.mnxfst.testing.consumer.async.AsyncInputConsumerStatistics;
import com.mnxfst.testing.consumer.async.IAsyncInputConsumer;
import com.mnxfst.testing.consumer.exception.AsyncInputConsumerException;
import com.mnxfst.testing.consumer.jms.analyzer.ESPMessageAnalyzer;
import com.mnxfst.testing.consumer.jms.event.JMSMessageEvent;

/**
 * Implements a simple JMS destination consumer 
 * @author mnxfst
 *
 */
public class JMSConsumerHandler implements IAsyncInputConsumer, MessageListener {

	private static final Logger logger = Logger.getLogger(JMSConsumerHandler.class.getName());
	
	private enum JMSDestinationType implements Serializable {
		TOPIC, QUEUE;
	}
	
	private static final String REQUEST_PARAMETER_INITIAL_CONTEXT_FACTORY = "initialCtxFactory";
	private static final String REQUEST_PARAMETER_CONNECTION_FACTORY_NAME = "connectionFactoryName";
	private static final String REQUEST_PARAMETER_JMS_DESTINATION = "destination";
	private static final String REQUEST_PARAMETER_PROVIDER_URL = "providerUrl";
	private static final String REQUEST_PARAMETER_SECURITY_PRINCIPAL = "secPrincipal";
	private static final String REQUEST_PARAMETER_SECURITY_CREDENTIALS = "secCredentials";
	private static final String REQUEST_PARAMETER_JMS_DESTINATION_TYPE = "type";
	private static final String REQUEST_PARAMETER_JMS_MESSAGE_SELECTOR = "messageSelector";
	
	private static final String REQUEST_PARAMETER_VENDOR_SPECIFC_PREFIX = "vendor-";
	private static final String REQUEST_PARAMETER_JMS_MESSAGE_ANALYZERS = "jmsMsgAnalyzers";
	
	private static final String CONFIG_PROPS_JMS_MESSAGE_ANALYZERS_PREFIX = "consumer.async.jms.message-analyzer.";	
	
	private String id = null;
	private String type = null;

	private ConnectionFactory connectionFactory = null;
	private Connection connection = null;
	private Session session = null;
	private Destination destination = null;
	private JMSDestinationType jmsDestinationType = JMSDestinationType.QUEUE;
	private String messageSelector = null;
	
	private int messagesReceived = 0;
	private boolean running = false; 
	
	
//	private static ExecutorService jmsMessageAnalyzerExecService = Executors.newCachedThreadPool();
//	private static ConcurrentMap<String, IMessageAnalyzer> runningAnalyzers = new ConcurrentHashMap<String, IMessageAnalyzer>();

	private Set<String> activatedAnalyzers = new HashSet<String>(); // TODO maybe we could get the other ones to sleep

	private RingBuffer<JMSMessageEvent> jmsMessageEventRingBuffer = null;
	private final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);
	private static final int BUFFER_SIZE = 1024 * 8;
	

	

	/**
	 * @see com.mnxfst.testing.consumer.async.IAsyncInputConsumer#initialize(java.util.Map)
	 */
	public void initialize(Map<String, List<String>> properties) throws AsyncInputConsumerException {
		Hashtable<String, String> jndiEnvironment =  new Hashtable<String, String>();
		
		String initialContextFactoryClass = extractSingleString(REQUEST_PARAMETER_INITIAL_CONTEXT_FACTORY, properties);
		if(initialContextFactoryClass == null || initialContextFactoryClass.isEmpty())
			throw new AsyncInputConsumerException("Missing required initial context factory class");
		
		String connectionFactoryName = extractSingleString(REQUEST_PARAMETER_CONNECTION_FACTORY_NAME, properties);
		if(connectionFactoryName == null || connectionFactoryName.isEmpty())
			throw new AsyncInputConsumerException("Missing required connection factory lookup name");
		
		String providerUrl = extractSingleString(REQUEST_PARAMETER_PROVIDER_URL, properties);
		if(providerUrl == null || providerUrl.isEmpty())
			throw new AsyncInputConsumerException("Missing required provider url");
		
		String jmsDestination = extractSingleString(REQUEST_PARAMETER_JMS_DESTINATION, properties);
		if(jmsDestination == null || jmsDestination.isEmpty())
			throw new AsyncInputConsumerException("Missing required JMS destination lookup name");
		
		String tmp = extractSingleString(REQUEST_PARAMETER_JMS_DESTINATION_TYPE, properties);
		if(tmp != null && tmp.trim().equalsIgnoreCase("topic"))
			jmsDestinationType = JMSDestinationType.TOPIC;
		else
			jmsDestinationType = JMSDestinationType.QUEUE;
		
		messageSelector = extractSingleString(REQUEST_PARAMETER_JMS_MESSAGE_SELECTOR, properties);
		
		String securityPrincipal = extractSingleString(REQUEST_PARAMETER_SECURITY_PRINCIPAL, properties);
		String securityCredentials = extractSingleString(REQUEST_PARAMETER_SECURITY_CREDENTIALS, properties);
		
		

		jndiEnvironment.put(Context.INITIAL_CONTEXT_FACTORY, initialContextFactoryClass);
		jndiEnvironment.put(Context.PROVIDER_URL, providerUrl);
		if(securityCredentials != null && !securityCredentials.isEmpty())
			jndiEnvironment.put(Context.SECURITY_CREDENTIALS, securityCredentials);
		if(securityPrincipal != null && !securityPrincipal.isEmpty())
			jndiEnvironment.put(Context.SECURITY_PRINCIPAL, securityPrincipal);
		
		jndiEnvironment.putAll(extractVendorSpecificValues(properties));

		logger.info("JMSType: " + jmsDestinationType + ", selector = " + messageSelector);
		
		if(logger.isDebugEnabled())
			logger.debug("jmsConsumer[id="+id+", type="+type+", initialCtxFactory="+initialContextFactoryClass+", connectionFactory="+connectionFactoryName+", providerUrl="+providerUrl+", jmsDestination="+jmsDestination+", type="+jmsDestinationType+", selector=("+messageSelector+")]");

		try {
			// create initial context from collected settings
			InitialContext ctx = new InitialContext(jndiEnvironment);
			
			// lookup connection factory, create a connection and create a new session
			connectionFactory = (ConnectionFactory)ctx.lookup(connectionFactoryName);
			connection = connectionFactory.createConnection();
			try {
				connection.setClientID("jmsConsumer@"+InetAddress.getLocalHost().getHostName());
			} catch(Exception e) {
				logger.error("jmsConsumer[id="+this.id+", type="+this.type+"]: host name lookup failed. Client id will not be set for JMS connection. Error: " + e.getMessage());
			}
			session = connection.createSession(false,  Session.AUTO_ACKNOWLEDGE);
			
			// find desired destination and create a message consumer
			destination = (Destination)ctx.lookup(jmsDestination);
			
			switch(jmsDestinationType) {
				case TOPIC: {
					
					TopicSubscriber topicMessageConsumer = null;
					if(messageSelector != null && !messageSelector.isEmpty()) {
						topicMessageConsumer = (TopicSubscriber)session.createConsumer(destination, messageSelector);
						logger.info("topicMessageConsumer[destination="+jmsDestination+", messageSelector="+messageSelector+"]");
					} else {
						topicMessageConsumer = (TopicSubscriber)session.createConsumer(destination);
						logger.info("topicMessageConsumer[destination="+jmsDestination+", messageSelector=not provided]");
					}
					topicMessageConsumer.setMessageListener(this);
					break;
				}
				default: {
					MessageConsumer messageConsumer = session.createConsumer(destination);
					messageConsumer.setMessageListener(this);
					logger.info("queueMessageConsumer[destination="+jmsDestination+"]");
				}
			}	
			
			
			
			// start listening
			connection.start();

			if(logger.isDebugEnabled())
				logger.debug("jmsConsumer[id="+this.id+", type="+this.type+", providerUrl="+providerUrl+", jmsDestination="+destination+", initialCtxFactory="+initialContextFactoryClass+", connectionFactoryName="+connectionFactoryName+", clientId="+connection.getClientID()+"]");//, analyzers="+runningAnalyzers.size()+"]");
			logger.info("jmsConsumer[id="+this.id+", type="+this.type+", providerUrl="+providerUrl+", jmsDestination="+destination+", initialCtxFactory="+initialContextFactoryClass+", connectionFactoryName="+connectionFactoryName+", clientId="+connection.getClientID()+"]");//, analyzers="+runningAnalyzers.size()+"]");

		} catch(NamingException e) {
			logger.error("Failed to initialize naming context, lookup required objects and establish a connection. Error: " + e.getMessage(), e);
			throw new AsyncInputConsumerException("Failed to initialize naming context, lookup required objects and establish a connection. Error: " + e.getMessage());
		} catch (JMSException e) {
			logger.error("Failed to initialize naming context, lookup required objects and establish a connection. Error: " + e.getMessage(), e);
			throw new AsyncInputConsumerException("Failed to initialize naming context, lookup required objects and establish a connection. Error: " + e.getMessage());
		}
		
		jmsMessageEventRingBuffer = new RingBuffer<JMSMessageEvent>(JMSMessageEvent.EVENT_FACTORY, new SingleThreadedClaimStrategy(BUFFER_SIZE), new SleepingWaitStrategy());
		SequenceBarrier barrier = jmsMessageEventRingBuffer.newBarrier();
		ESPMessageAnalyzer analyzer = new ESPMessageAnalyzer();
		analyzer.initialize(properties);
		BatchEventProcessor<JMSMessageEvent> eventProcessor = new BatchEventProcessor<JMSMessageEvent>(jmsMessageEventRingBuffer, barrier, analyzer);
		jmsMessageEventRingBuffer.setGatingSequences(eventProcessor.getSequence());
		EXECUTOR.submit(eventProcessor);
	}

	public AsyncInputConsumerStatistics getConsumerStatistics() {
		return new AsyncInputConsumerStatistics();
	}
	

	/**
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
	}


	/**
	 * @see javax.jms.MessageListener#onMessage(javax.jms.Message)
	 */
	public void onMessage(Message message) {
		
		try {
			if(message != null && message instanceof TextMessage) {
				long sequence = jmsMessageEventRingBuffer.next();
				JMSMessageEvent event = jmsMessageEventRingBuffer.get(sequence);
				event.setMessageText(((TextMessage)message).getText());
				event.setTimestamp(System.currentTimeMillis());
				jmsMessageEventRingBuffer.publish(sequence);
			}
		} catch(JMSException e) {
			logger.error("Failed to process JMS text message: " + e.getMessage());
		}
		
	}


	/**
	 * @see com.mnxfst.testing.consumer.async.IAsyncInputConsumer#shutdown()
	 */
	public void shutdown() throws AsyncInputConsumerException {
		running = false;
		try {
			connection.stop();
			session.close();
		} catch (JMSException e) {
			throw new AsyncInputConsumerException("Failed to shutdown " + JMSConsumerHandler.class.getName() + " (id="+id+") properly. Error: " + e.getMessage());
		} 
		
		if(logger.isDebugEnabled())
			logger.debug("Successfully shut down " + JMSConsumerHandler.class.getName());
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
	 * Returns an array of strings containing the values received for the referenced parameter 
	 * @param parameter
	 * @param queryParams
	 * @return
	 * @throws AsyncInputConsumerException thrown in case there are no values 
	 */
	protected String[] extractMultiParameterValues(String parameter, Map<String, List<String>> queryParams) {
		
		List<String> values = queryParams.get(parameter);
		if(values != null && !values.isEmpty()) {
			String[] result = new String[values.size()];
			for(int i = 0; i < values.size(); i++) {
				String v = values.get(i);				
				result[i] = (v != null ? v.trim() : "");
			}
			return result;
		}
		
		return null;
	}

	/**
	 * Iterates through parameters, checks if each is prefixed with the vendor specific property key and extracts
	 * the value if necessary
	 * @param queryParams
	 * @return
	 */
	protected Map<String, String> extractVendorSpecificValues(Map<String, List<String>> queryParams) {
		
		Map<String, String> vendorSpecificSettings = new HashMap<String, String>();
		if(logger.isDebugEnabled())
			logger.debug("Vendor specific JMS settings");
		
		for(String parameter : queryParams.keySet()) {
			if(parameter.startsWith(REQUEST_PARAMETER_VENDOR_SPECIFC_PREFIX)) {
				String value = extractSingleString(parameter, queryParams);
				if(value != null) {
					vendorSpecificSettings.put(parameter.substring(REQUEST_PARAMETER_VENDOR_SPECIFC_PREFIX.length()), value);
					if(logger.isDebugEnabled())
						logger.debug(parameter.substring(REQUEST_PARAMETER_VENDOR_SPECIFC_PREFIX.length()) + " = " + value);

				}
			}
		}
		
		return vendorSpecificSettings;
	}

		

	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @param id the id to set
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * @return the type
	 */
	public String getType() {
		return type;
	}

	/**
	 * @param type the type to set
	 */
	public void setType(String type) {
		this.type = type;
	}

}

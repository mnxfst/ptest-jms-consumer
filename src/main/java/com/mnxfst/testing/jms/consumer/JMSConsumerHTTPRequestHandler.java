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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.jms.JMSException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.jboss.netty.util.CharsetUtil;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.mnxfst.testing.jms.consumer.config.JMSConsumerConfigBuilder;
import com.mnxfst.testing.jms.exception.JMSConsumerConfigException;
import com.mnxfst.testing.jms.exception.JMSConsumerStartException;

/**
 * Provides a handler for incoming HTTP requests
 * @author mnxfst
 * @since 20.02.2012
 */
public class JMSConsumerHTTPRequestHandler extends SimpleChannelUpstreamHandler {

	private static final Logger logger = Logger.getLogger(JMSConsumerHTTPRequestHandler.class);
	
	private static final String REQUEST_PARAM_START_CONSUMER = "startConsumer";
	private static final String REQUEST_PARAM_STOP_CONSUMER = "stopConsumer";
	private static final String REQUEST_PARAM_REPORT_CONSUMER = "reportConsumer";
	private static final String REQUEST_PARAM_JMS_CONSUMER_IDENTIFIER = "consumerId";
	private static final String REQUEST_PARAM_INIT_CTX_FACTORY = "initialCtxFactory";
	private static final String REQUEST_PARAM_CONNECTION_FACTORY_NAME = "connectionFactoryName";
	private static final String REQUEST_PARAM_BROKER_URL = "brokerUrl";
	private static final String REQUEST_PARAM_SECURITY_PRINCIPAL = "principal";
	private static final String REQUEST_PARAM_SECURITY_CREDENTIALS = "credentials";
	private static final String REQUEST_PARAM_DESTINATION = "destination";
	private static final String REQUEST_PARAM_VENDOR_SPECIFIC_PREFIX = "vendor-";
	
	private static final int RESPONSE_CODE_CONSUMER_STARTED = 1;
	private static final int RESPONSE_CODE_CONSUMER_STOPPED = 2;
	private static final int RESPONSE_CODE_REPORT_CONSUMER = 3;
	private static final int RESPONSE_CODE_REPORT_CONSUMER_FAILED = 4;
	private static final int RESPONSE_CODE_CONSUMER_START_FAILED = 5;
	private static final int RESPONSE_CODE_CONSUMER_STOP_FAILED = 6;
	private static final int RESPONSE_CODE_GENERAL_ERROR = 7;
	
	private static final int ERROR_CODE_UNKNOWN_OP_CODE = 1;
	private static final int ERROR_CODE_JNDI_LOOKUP_FAILURE = 2;
	private static final int ERROR_CODE_NO_CONSUMER_ID = 3;
	private static final int ERROR_CODE_THREAD_FAILED_TO_START = 4;
	private static final int ERROR_CODE_GENERAL_JMS_ERROR = 5;
	private static final int ERROR_CODE_CONSUMER_ID_MISSING = 6;
	private static final int ERROR_CODE_CONSUMER_NOT_FOUND = 7;
	private static final int ERROR_CODE_CONSUMER_CONFIG_MISSING = 8;
	private static final int ERROR_CODE_CONSUMER_CONFIG_INVALID = 9;
	private static final int ERROR_CODE_INITIAL_CTX_MISSING = 10;
	private static final int ERROR_CODE_CONNECTION_FACTORY_NAME_MISSING = 11;
	private static final int ERROR_CODE_BROKER_URL_MISSING = 12;
	private static final int ERROR_CODE_DESTINATION = 13;
	
	
	private static ExecutorService jmsConsumerExecutorService = Executors.newCachedThreadPool();
	private static ConcurrentMap<String, JMSConsumerRunnable> runningConsumers = new ConcurrentHashMap<String, JMSConsumerRunnable>(); 
	
	
	/**
	 * @see org.jboss.netty.channel.SimpleChannelUpstreamHandler#messageReceived(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.MessageEvent)
	 */
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent event) throws Exception {

		// extract http request from incoming message, get keep alive attribute as it will be transferred to response and decode query string 		
		HttpRequest httpRequest = (HttpRequest)event.getMessage();
		
		boolean keepAlive = HttpHeaders.Values.KEEP_ALIVE.equalsIgnoreCase(httpRequest.getHeader(HttpHeaders.Names.CONNECTION));		
		QueryStringDecoder decoder = new QueryStringDecoder(httpRequest.getUri());

		// fetch query parameters
		Map<String, List<String>> queryParams = decoder.getParameters();
		
		// handle post request
		if(httpRequest.getMethod() == HttpMethod.POST) {
			decoder = new QueryStringDecoder("?" + httpRequest.getContent().toString(CharsetUtil.UTF_8));
			queryParams.putAll(decoder.getParameters());
		}

		if(queryParams.containsKey(REQUEST_PARAM_START_CONSUMER)) {
			startConsumer(queryParams, keepAlive, event);				
		} else if(queryParams.containsKey(REQUEST_PARAM_STOP_CONSUMER)) {
			stopConsumer(queryParams, keepAlive, event);
		} else if(queryParams.containsKey(REQUEST_PARAM_REPORT_CONSUMER)) {
			reportConsumer(queryParams, keepAlive, event);
		} else {
			sendResponse(generateErrorMessage(RESPONSE_CODE_GENERAL_ERROR, new int[]{ERROR_CODE_UNKNOWN_OP_CODE}, ""), keepAlive, event);
		}
	}
	
	/**
	 * Starts a new JMS consumer
	 * @param queryParams
	 * @return
	 * @throws JMSConsumerStartException
	 */
	protected void startConsumer(Map<String, List<String>> queryParams, boolean keepAlive, MessageEvent event) {
		
//		String initialContextFactory = "org.apache.activemq.jndi.ActiveMQInitialContextFactory";
//		String connectionFactoryName = "ConnectionFactory"; 
//		String brokerUrl = "tcp://localhost:61616"; 
//		String securityPrincipal = null;
//		String securityCredentials = null; 
//		String jmsDestinationName = "espTopic";

//		private static final String REQUEST_PARAM_DESTINATION = "destination";
//		private static final String REQUEST_PARAM_VENDOR_SPECIFIC_PREFIX = "vendor-";

		Hashtable<String, String> jndiEnvironment = new Hashtable<String, String>();
		List<String> values = queryParams.get(REQUEST_PARAM_INIT_CTX_FACTORY);
		String initialCtxFactory = (values != null && values.size() > 0 ? values.get(0): null);
		if(initialCtxFactory == null || initialCtxFactory.isEmpty()) {
			sendResponse(generateErrorMessage(RESPONSE_CODE_CONSUMER_START_FAILED, new int[]{ERROR_CODE_INITIAL_CTX_MISSING}, null), keepAlive, event);
			return;
		}
		jndiEnvironment.put(Context.INITIAL_CONTEXT_FACTORY, initialCtxFactory);
		
		values = queryParams.get(REQUEST_PARAM_CONNECTION_FACTORY_NAME);
		String connectionFactoryName = (values != null && values.size() > 0 ? values.get(0): null);
		if(connectionFactoryName == null || connectionFactoryName.isEmpty()) {
			sendResponse(generateErrorMessage(RESPONSE_CODE_CONSUMER_START_FAILED, new int[]{ERROR_CODE_CONNECTION_FACTORY_NAME_MISSING}, null), keepAlive, event);
			return;
		}
		
		values = queryParams.get(REQUEST_PARAM_BROKER_URL);
		String brokerUrl = (values != null && values.size() > 0 ? values.get(0): null);
		if(brokerUrl == null || brokerUrl.isEmpty()) {
			sendResponse(generateErrorMessage(RESPONSE_CODE_CONSUMER_START_FAILED, new int[]{ERROR_CODE_BROKER_URL_MISSING}, null), keepAlive, event);
			return;
		}
		jndiEnvironment.put(Context.PROVIDER_URL, brokerUrl);
		
		values = queryParams.get(REQUEST_PARAM_SECURITY_PRINCIPAL);
		String principal = (values != null && values.size() > 0 ? values.get(0): null);
		if(principal != null && !principal.isEmpty())
			jndiEnvironment.put(Context.SECURITY_PRINCIPAL, principal);
		
		values = queryParams.get(REQUEST_PARAM_SECURITY_CREDENTIALS);
		String credentials = (values != null && values.size() > 0 ? values.get(0): null);
		if(credentials != null && !credentials.isEmpty())
			jndiEnvironment.put(Context.SECURITY_CREDENTIALS, credentials);
		
		values = queryParams.get(REQUEST_PARAM_DESTINATION);
		String destination = (values != null && values.size() > 0 ? values.get(0): null);
		if(destination == null || destination.isEmpty()) {
			sendResponse(generateErrorMessage(RESPONSE_CODE_CONSUMER_START_FAILED, new int[]{ERROR_CODE_DESTINATION}, null), keepAlive, event);
			return;
		}
		
		for(String key : queryParams.keySet()) {
			if(key.startsWith(REQUEST_PARAM_VENDOR_SPECIFIC_PREFIX)) {
				values = queryParams.get(key);
				if(values != null && values.size() > 0)
					jndiEnvironment.put(key.substring(REQUEST_PARAM_VENDOR_SPECIFIC_PREFIX.length()), values.get(0));
			}
		}
		
//		Document doc = null;
//		Hashtable<String, String> jndiEnvironment = null;
//		try {
//			doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(configuration.getBytes()));
//			jndiEnvironment = JMSConsumerConfigBuilder.getInstance().parseConfiguration(doc);
//		} catch(JMSConsumerConfigException e) {
//			sendResponse(generateErrorMessage(RESPONSE_CODE_CONSUMER_START_FAILED, new int[]{ERROR_CODE_CONSUMER_CONFIG_INVALID}, e.getMessage()), keepAlive, event);
//			return;
//		} catch(IOException e) {
//			sendResponse(generateErrorMessage(RESPONSE_CODE_CONSUMER_START_FAILED, new int[]{ERROR_CODE_CONSUMER_CONFIG_INVALID}, e.getMessage()), keepAlive, event);
//			return;
//		} catch (SAXException e) {
//			sendResponse(generateErrorMessage(RESPONSE_CODE_CONSUMER_START_FAILED, new int[]{ERROR_CODE_CONSUMER_CONFIG_INVALID}, e.getMessage()), keepAlive, event);
//			return;
//		} catch (ParserConfigurationException e) {
//			sendResponse(generateErrorMessage(RESPONSE_CODE_CONSUMER_START_FAILED, new int[]{ERROR_CODE_CONSUMER_CONFIG_INVALID}, e.getMessage()), keepAlive, event);
//			return;
//		}
//			

		try {
			JMSConsumerRunnable consumerRunnable = new JMSConsumerRunnable(jndiEnvironment, connectionFactoryName, destination);
			Future<String> consumerId = jmsConsumerExecutorService.submit(consumerRunnable);
			if(consumerId != null) {
				try {
					String identifier = consumerId.get();
					if(identifier != null && !identifier.isEmpty()) {
						runningConsumers.putIfAbsent(identifier, consumerRunnable);
						sendResponse(generateSuccessMessage(RESPONSE_CODE_CONSUMER_STARTED, identifier, consumerRunnable.getUpTime(), consumerRunnable.getStart(), consumerRunnable.getEnd(), consumerRunnable.getMessagesReceived()), keepAlive, event);
						return;						
					} else {
						consumerRunnable.stop();
						sendResponse(generateErrorMessage(RESPONSE_CODE_CONSUMER_START_FAILED, new int[]{ERROR_CODE_NO_CONSUMER_ID}, null), keepAlive, event);
					}
				} catch(ExecutionException e) {
					sendResponse(generateErrorMessage(RESPONSE_CODE_CONSUMER_START_FAILED, new int[]{ERROR_CODE_THREAD_FAILED_TO_START}, null), keepAlive, event);
				} catch (InterruptedException e) {
					sendResponse(generateErrorMessage(RESPONSE_CODE_CONSUMER_START_FAILED, new int[]{ERROR_CODE_THREAD_FAILED_TO_START}, null), keepAlive, event);				
				}
			}
			consumerRunnable.stop();
			sendResponse(generateErrorMessage(RESPONSE_CODE_CONSUMER_START_FAILED, new int[]{ERROR_CODE_NO_CONSUMER_ID}, null), keepAlive, event);
		} catch(NamingException e) {
			sendResponse(generateErrorMessage(RESPONSE_CODE_CONSUMER_START_FAILED, new int[]{ERROR_CODE_JNDI_LOOKUP_FAILURE}, e.getMessage()), keepAlive, event);
		} catch(JMSException e) {			
			sendResponse(generateErrorMessage(RESPONSE_CODE_CONSUMER_START_FAILED, new int[]{ERROR_CODE_GENERAL_JMS_ERROR}, e.getMessage()), keepAlive, event);
		}
	}
	
	/**
	 * Stops the referenced consumer	
	 * @param queryParams
	 * @param keepAlive
	 * @param event
	 */
	protected void stopConsumer(Map<String, List<String>> queryParams, boolean keepAlive, MessageEvent event) {
		List<String> values = queryParams.get(REQUEST_PARAM_JMS_CONSUMER_IDENTIFIER);
		String consumerIdentifier = (values != null && values.size() > 0 ? values.get(0): null);
		if(consumerIdentifier != null && !consumerIdentifier.isEmpty()) {
			JMSConsumerRunnable jmsConsumer = runningConsumers.get(consumerIdentifier);
			if(jmsConsumer != null) {
				try {
					jmsConsumer.stop();
					runningConsumers.remove(consumerIdentifier);
					sendResponse(generateSuccessMessage(RESPONSE_CODE_CONSUMER_STOPPED, consumerIdentifier, jmsConsumer.getUpTime(), jmsConsumer.getStart(), jmsConsumer.getEnd(), jmsConsumer.getMessagesReceived()), keepAlive, event);
				} catch(JMSException e) {
					sendResponse(generateErrorMessage(RESPONSE_CODE_CONSUMER_STOP_FAILED, new int[]{ERROR_CODE_GENERAL_JMS_ERROR}, null), keepAlive, event);
				}
			} else {
				sendResponse(generateErrorMessage(RESPONSE_CODE_CONSUMER_STOP_FAILED, new int[]{ERROR_CODE_CONSUMER_NOT_FOUND}, null), keepAlive, event);
			}
		} else {
			sendResponse(generateErrorMessage(RESPONSE_CODE_CONSUMER_STOP_FAILED, new int[]{ERROR_CODE_CONSUMER_ID_MISSING}, null), keepAlive, event);
		} 
	}
	
	/**
	 * Sends a report for the referenced consumer
	 * @param queryParams
	 * @param keepAlive
	 * @param event
	 */
	protected void reportConsumer(Map<String, List<String>> queryParams, boolean keepAlive, MessageEvent event) {
		List<String> values = queryParams.get(REQUEST_PARAM_JMS_CONSUMER_IDENTIFIER);
		String consumerIdentifier = (values != null && values.size() > 0 ? values.get(0): null);
		if(consumerIdentifier != null && !consumerIdentifier.isEmpty()) {
			JMSConsumerRunnable jmsConsumer = runningConsumers.get(consumerIdentifier);
			if(jmsConsumer != null) {
				sendResponse(generateSuccessMessage(RESPONSE_CODE_REPORT_CONSUMER, consumerIdentifier, jmsConsumer.getUpTime(), jmsConsumer.getStart(), jmsConsumer.getEnd(), jmsConsumer.getMessagesReceived()), keepAlive, event);
			} else {
				sendResponse(generateErrorMessage(RESPONSE_CODE_REPORT_CONSUMER_FAILED, new int[]{ERROR_CODE_CONSUMER_NOT_FOUND}, null), keepAlive, event);
			}
		} else {
			sendResponse(generateErrorMessage(RESPONSE_CODE_REPORT_CONSUMER_FAILED, new int[]{ERROR_CODE_CONSUMER_ID_MISSING}, null), keepAlive, event);
		} 		
	}
	
	
	
	/**
	 * Generates an error response
	 * @param responseCode
	 * @param errorCodes
	 * @param errorMessage
	 * @return
	 */
	protected String generateErrorMessage(int responseCode, int[] errorCodes, String errorMessage) {
		StringBuffer buffer = new StringBuffer("<jmsConsumerResponse>");		
		buffer.append("<responseCode>").append(responseCode).append("</responseCode>");
		buffer.append("<errorCodes>");
		if(errorCodes != null && errorCodes.length > 0) {
			for(int i = 0; i < errorCodes.length; i++)
				buffer.append("<errorCode>").append(errorCodes[i]).append("</errorCode>");
		}
		buffer.append("</errorCodes>");
		buffer.append("<message>").append((errorMessage != null ? errorMessage : "")).append("</message>");		
		buffer.append("</jmsConsumerResponse>");
		return buffer.toString();		
	}
		
	/**
	 * Generates a success message
	 * @param responseCode
	 * @param consumerIdentifier
	 * @return
	 */
	protected String generateSuccessMessage(int responseCode, String consumerIdentifier, long uptime, long consumerStart, long consumerEnd, long messagesReceived) {
		StringBuffer buffer = new StringBuffer("<jmsConsumerResponse>");		
		buffer.append("<responseCode>").append(responseCode).append("</responseCode>");
		buffer.append("<consumerId>").append(consumerIdentifier).append("</consumerId>");
		buffer.append("<uptime>").append(uptime).append("</uptime>");
		buffer.append("<start>").append(consumerStart).append("</start>");
		buffer.append("<end>").append(consumerEnd).append("</end>");		
		buffer.append("<messagesReceived>").append(messagesReceived).append("</messagesReceived>");
		buffer.append("<runningConsumers>").append(runningConsumers.size()).append("</runningConsumers>");
		buffer.append("</jmsConsumerResponse>");
		return buffer.toString();		
	}
	

	/**
	 * Sends a response containing the given message to the calling client
	 * @param responseMessage
	 * @param keepAlive
	 * @param event
	 */
	protected void sendResponse(String responseMessage, boolean keepAlive, MessageEvent event) {
		HttpResponse httpResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
		
		httpResponse.setContent(ChannelBuffers.copiedBuffer(responseMessage, CharsetUtil.UTF_8));
		httpResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");
		
		if(keepAlive)
			httpResponse.setHeader(HttpHeaders.Names.CONTENT_LENGTH, httpResponse.getContent().readableBytes());
		
		ChannelFuture future = event.getChannel().write(httpResponse);
		if(!keepAlive)
			future.addListener(ChannelFutureListener.CLOSE);
	}

	/**
	 * @see org.jboss.netty.channel.SimpleChannelUpstreamHandler#exceptionCaught(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.ExceptionEvent)
	 */
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
		super.exceptionCaught(ctx, e);		
		logger.error("Error while processing http request: " + e.getCause().getMessage(), e.getCause());
	}
	
}

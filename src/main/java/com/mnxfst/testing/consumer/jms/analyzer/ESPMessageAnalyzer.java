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
package com.mnxfst.testing.consumer.jms.analyzer;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.activemq.util.ByteArrayInputStream;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.ibm.icu.text.SimpleDateFormat;
import com.ibm.icu.util.TimeZone;
import com.mnxfst.testing.consumer.exception.AsyncInputConsumerException;
import com.mnxfst.testing.consumer.jms.IMessageAnalyzer;

/**
 * esp project specifc log analyzer
 * @author mnxfst
 * @since 22.02.2012
 */
public class ESPMessageAnalyzer implements IMessageAnalyzer {

	private static final Logger logger = Logger.getLogger(ESPMessageAnalyzer.class.getName());			

	private static final String CFG_PROP_NODE_ID = "nodeId";
	private static final String CFG_PROP_MEASURING_POINT_ID = "measuringPointId";
	private static final String CFG_PROP_REQUIRED_DOMAIN_SIGN = "requiredDomainSign";
	
	private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
	
	private String nodeId = null;
	private String measuringPointId = null;
	private String requiredDomainSign = null;
	
	private XPathExpression requestIdExpression = null;
	private XPathExpression domainSignExpression = null;
	private XPathExpression titleExpression = null;
	private XPathExpression materialGroupExpression = null;
	private DocumentBuilder documentBuilder = null;
	
	
	private boolean running = false;
	
	/**
	 * @see com.mnxfst.testing.consumer.jms.IMessageAnalyzer#initialize(java.util.Map)
	 */
	public void initialize(Map<String, List<String>> configuration) throws AsyncInputConsumerException {
		
		this.nodeId = extractSingleString(CFG_PROP_NODE_ID, configuration);
		if(this.nodeId == null || this.nodeId.isEmpty())
			throw new AsyncInputConsumerException("Missing required configuration option 'nodeId'");
		this.measuringPointId = extractSingleString(CFG_PROP_MEASURING_POINT_ID, configuration);
		if(this.measuringPointId == null || this.measuringPointId.isEmpty())
			throw new AsyncInputConsumerException("Missing required configuration option 'measuringPointId'");
		this.requiredDomainSign = extractSingleString(CFG_PROP_REQUIRED_DOMAIN_SIGN, configuration);
		if(this.requiredDomainSign == null || this.requiredDomainSign.isEmpty())
			throw new AsyncInputConsumerException("Missing required configuration option 'requiredDomainSign'");
		
		
		// switch to utc
		dateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		
		// compile xpath expressions
		XPath xpath = XPathFactory.newInstance().newXPath();		
		try {
			this.requestIdExpression = xpath.compile("//Id//text()");
			this.domainSignExpression = xpath.compile("//domainSign/text()");
			this.titleExpression = xpath.compile("//title/text()");
			this.materialGroupExpression = xpath.compile("//materialGroup/text()");
			this.documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		} catch (XPathExpressionException e) {
			throw new AsyncInputConsumerException("Failed to compute xpath expressions required for JMS message content analysis");
		} catch (ParserConfigurationException e) {
			throw new AsyncInputConsumerException("Failed to set up document builder required for JMS message content analysis");
		}
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


	public void onMessage(Message message) {
		if(message != null) {
			if(message instanceof TextMessage) {
				try {
					long incomingTime = System.currentTimeMillis();
					String msg = ((TextMessage)message).getText();
					if(msg != null && !msg.isEmpty()) {						
						Document document = this.documentBuilder.parse(new ByteArrayInputStream(msg.getBytes()));
						String requestId = null;
						try {
							requestId = (String)requestIdExpression.evaluate(document, XPathConstants.STRING);
						} catch (XPathExpressionException e) {
							requestId = "-1_requestParameter_is_missing";
						}

						String domainSign = null;
						try {
							domainSign = (String)domainSignExpression.evaluate(document, XPathConstants.STRING);
						} catch (XPathExpressionException e) {
							//
						}
						
						String title = null;
						try {
							title = (String)titleExpression.evaluate(document, XPathConstants.STRING);
						} catch(XPathExpressionException e) {
							//
						}
						
						String materialGroup = null;
						try {
							materialGroup = (String)materialGroupExpression.evaluate(document, XPathConstants.STRING);
						} catch(XPathExpressionException e) {
							//
						}
						
						boolean validMessage = (requestId != null && !requestId.isEmpty()); 
						if(validMessage)
							validMessage = (domainSign != null && domainSign.equalsIgnoreCase(requiredDomainSign));
						if(validMessage)
							validMessage = (title != null && !title.isEmpty());
						if(validMessage)
							validMessage = (materialGroup != null && !materialGroup.isEmpty());
						
						StringBuffer logBuffer = new StringBuffer();
						logBuffer.append(requestId).append(";").append(nodeId).append(";").append(measuringPointId).append(";").append(incomingTime).append(";").append(dateFormatter.format(incomingTime)).append(";").append(validMessage).append(";true"); // TODO validate request
						logger.info(logBuffer.toString());
					}
				} catch (JMSException e) {
					logger.error("Failed to convert incoming message to text message representation. Error: " + e.getMessage());
				} catch (SAXException e) {
					logger.error("Failed to parse incoming message into XML DOM representation. Error: " + e.getMessage());
				} catch (IOException e) {
					logger.error("Failed to execute a necessary I/O operation. Error: " + e.getMessage());
				}
			} 
		}
	}

	/**
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		running = true;
		while(running);
		logger.error(ESPMessageAnalyzer.class.getName() + " shutdown");		
	}
	
	public void shutdown() {
		running = false;
	}

}

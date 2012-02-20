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

package com.mnxfst.testing.jms.consumer.config;

import java.util.Hashtable;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.mnxfst.testing.jms.exception.JMSConsumerConfigException;

/**
 * Parses the jms configuration from the provided xml and returns a hashtable to be used with the initial context
 * @author mnxfst
 *
 */
public class JMSConsumerConfigBuilder {

	private static final Logger logger = Logger.getLogger(JMSConsumerConfigBuilder.class);
	
	// XML document nodes
	public static final String CONFIG_ROOT_NODE = "jmsConsumerConfig";
	
	private static final String XPATH_EXPRESSION_ALL_JNDI_CONFIG_OPTIONS = "/jmsConsumerConfig/jndiOptions/*";
//	public static final String XPATH_EXPRESSION_JNDI_INIT_CONTEXT_FACTORY = "initialContextFactory";
//	public static final String XPATH_EXPRESSION_CONNECTION_FACTORY_NAME = "connectionFactoryName";
//	public static final String XPATH_EXPRESSION_BROKER_URL = "brokerUrl";
//	public static final String XPATH_EXPRESSION_SECURITY_PRINCIPAL = "securityPrincipal";
//	public static final String XPATH_EXPRESSION_SECURITY_CREDENTIALS = "securityCredentials";
//	public static final String XPATH_EXPRESSION_JMS_DESTINATION_NAME = "jmsDestination";
//	public static final String XPATH_EXPRESSION_JMS_DESTINATION_TYPE = "jmsDestinationType";
//	public static final String XPATH_EXPRESSION_TOPIC_MESSAGE_SELECTOR = "messageSelector";
	
	/*
	
		<jmsConsumerConfig>
			<jndiOptions>
				<initialContextFactory>clazzName</initialContextFactory>
				<connectionFactoryName>connectionFactory</connectionFactoryName>
				<brokerUrl>tcp://locahost:9121</brokerUrl>
				<securityPrincipal></securityPrincipal>
				<securityCredentials></securityCredentials>
				<jmsDestination>testTopic</jmsDestination>
				<jmsDestinationType>topic</jmsDestinationType>
				<messageSelector>dsad=dsada</messageSelector>
			</jndiOptions>
		</jmsConsumerConfig>
	
	 */
	/**
	 * Singleton holder
	 * @author mnxfst
	 *
	 */
	private static class JMSConsumerConfigBuildSingletonHolder {
		public static JMSConsumerConfigBuilder instance = new JMSConsumerConfigBuilder();
	}
	
	/**
	 * No direct instantiation allowed
	 */
	private JMSConsumerConfigBuilder() {		
	}
	
	/**
	 * Returns the builder singleton
	 * @return
	 */
	public static JMSConsumerConfigBuilder getInstance() {
		return JMSConsumerConfigBuildSingletonHolder.instance;
	}
	
	public Hashtable<String, String> parseConfiguration(Document input) throws JMSConsumerConfigException {

		// validate against null
		if(input == null)
			throw new JMSConsumerConfigException("Missing required configuration");
		
		// find root node
		Node rootNode = input.getFirstChild();
		if(rootNode == null)
			throw new JMSConsumerConfigException("No root node contained in consumer configuration");

		// validate root node name
		if(rootNode.getNodeName() == null || rootNode.getNodeName().isEmpty() || !rootNode.getNodeName().equalsIgnoreCase(CONFIG_ROOT_NODE))
			throw new JMSConsumerConfigException("Invalid root node name '"+rootNode.getNodeName()+"'. Expected: " + CONFIG_ROOT_NODE);
		
		NodeList childNodes = rootNode.getChildNodes();
		if(childNodes == null || childNodes.getLength() < 1)
			throw new JMSConsumerConfigException("Missing required child nodes to " + CONFIG_ROOT_NODE);
		
		return parseJndiOptions(input);
	}
	
	public Hashtable<String, String> parseJndiOptions(Document input) throws JMSConsumerConfigException {
		
		NodeList jndiOptions = null;
		XPath xpath = XPathFactory.newInstance().newXPath();
		try {
			jndiOptions = (NodeList)xpath.evaluate(XPATH_EXPRESSION_ALL_JNDI_CONFIG_OPTIONS, input, XPathConstants.NODESET);			
		}  catch(XPathExpressionException e) {
			throw new JMSConsumerConfigException("Failed to parse for jndi options. Error: " + e.getMessage());
		}
		
		if(jndiOptions != null && jndiOptions.getLength() > 0) {
			
			Hashtable<String, String> options = new Hashtable<String, String>();
			for(int i = 0; i < jndiOptions.getLength(); i++) {
				
				Node cn = jndiOptions.item(i);
				
				// handle node only if it is an element node
				if(cn.getNodeType() == Node.ELEMENT_NODE) {
					
					// fetch the option name => node name
					String optionName = cn.getNodeName().trim();
					
					String optionValue = null;
					
					// fetch the value nodes and check for elements
					NodeList valueNodes = cn.getChildNodes();
					if(valueNodes != null && valueNodes.getLength() > 0) {
						// fetch the first node by default 
						// TODO in the future we could maybe handle elements having more than one child elements themselves
						Node vn = valueNodes.item(0);
						
						if(vn != null) {
							if(vn.getNodeType() == Node.TEXT_NODE) {
								optionValue = vn.getNodeValue();
							} else if(vn.getNodeType() == Node.CDATA_SECTION_NODE) {
								optionValue = vn.getNodeValue();
							}
						}
					}
					
					options.put(optionName, optionValue);
				}
				
			}
			return options;
		}
		
		throw new JMSConsumerConfigException("Missing required jndi options");
		
	}
}

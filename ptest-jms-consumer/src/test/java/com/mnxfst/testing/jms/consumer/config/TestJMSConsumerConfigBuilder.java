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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Hashtable;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import junit.framework.Assert;

import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.mnxfst.testing.jms.exception.JMSConsumerConfigException;

/**
 * Test case for {@link JMSConsumerConfigBuilder}
 * @author mnxfst
 *
 */
public class TestJMSConsumerConfigBuilder {

	@Test
	public void testParseConfiguration() throws JMSConsumerConfigException, SAXException, IOException, ParserConfigurationException {
		
		JMSConsumerConfigBuilder instance = JMSConsumerConfigBuilder.getInstance();
		try {
			instance.parseConfiguration(null);
			Assert.fail("Invalid document");
		} catch(JMSConsumerConfigException e) {
			//
		}
		
		ByteArrayInputStream bin = new ByteArrayInputStream("<?xml version=\"1.0\" encoding=\"UTF-8\"?><test></test>".getBytes());
		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(bin);
		try {
			instance.parseConfiguration(doc);
			Assert.fail("Invalid document");
		} catch(JMSConsumerConfigException e) {
			//
		}

		bin = new ByteArrayInputStream("<?xml version=\"1.0\" encoding=\"UTF-8\"?><jmsConsumerConfig/>".getBytes());
		doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(bin);
		try {
			instance.parseConfiguration(doc);
			Assert.fail("Invalid document");
		} catch(JMSConsumerConfigException e) {
			//
		}

		bin = new ByteArrayInputStream("<?xml version=\"1.0\" encoding=\"UTF-8\"?><jmsConsumerConfig><jndiOptions/></jmsConsumerConfig>".getBytes());
		doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(bin);
		try {
			instance.parseConfiguration(doc);
			Assert.fail("Invalid document");
		} catch(JMSConsumerConfigException e) {
			//
		}

		bin = new ByteArrayInputStream("<?xml version=\"1.0\" encoding=\"UTF-8\"?><jmsConsumerConfig><jndiOptions><initialContextFactory>sAS</initialContextFactory></jndiOptions></jmsConsumerConfig>".getBytes());
		doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(bin);
		try {
			instance.parseConfiguration(doc);
		} catch(JMSConsumerConfigException e) {
			//
		}

		bin = new ByteArrayInputStream("<?xml version=\"1.0\" encoding=\"UTF-8\"?><jmsConsumerConfig><jndiOptions><initialContextFactory>sAS</initialContextFactory><com.webmethods.jms.naming.clientgroup>test</com.webmethods.jms.naming.clientgroup></jndiOptions></jmsConsumerConfig>".getBytes());
		doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(bin);
		Hashtable<String, String> result = instance.parseConfiguration(doc);
		for(String k : result.keySet())
			System.out.println(k + " = " + result.get(k));
		
	}
	
}

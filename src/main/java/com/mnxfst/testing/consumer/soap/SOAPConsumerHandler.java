/*  ptest-server and client provides you with a performance test utility
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

package com.mnxfst.testing.consumer.soap;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.jboss.netty.handler.codec.http.HttpRequest;

import com.mnxfst.testing.consumer.exception.HttpRequestProcessingException;
import com.mnxfst.testing.consumer.handler.HttpRequestHandlerStatistics;
import com.mnxfst.testing.consumer.handler.IHttpRequestHandler;

/**
 * Provides a VERY SIMPLE SOAP handler
 * @author mnxfst
 * @since 23.02.2012
 */
public class SOAPConsumerHandler implements IHttpRequestHandler {

	private static final Logger logger = Logger.getLogger(SOAPConsumerHandler.class.getName());
	
	private String id = null;
	private String type = null;
	private boolean running = false;
	
	private long count = 0;
	
	/**
	 * @see com.mnxfst.testing.consumer.handler.IHttpRequestHandler#initialize(java.util.Properties, java.util.Map)
	 */
	public void initialize(Properties configuration, Map<String, List<String>> queryParameters) throws HttpRequestProcessingException {
	}

	/**
	 * @see com.mnxfst.testing.consumer.handler.IHttpRequestHandler#processRequest(org.jboss.netty.handler.codec.http.HttpRequest, java.util.Map)
	 */
	public byte[] processRequest(HttpRequest request, Map<String, List<String>> queryParameters) throws HttpRequestProcessingException {
		count = count + 1;
		String response = "<response>"+count+"</response>";
		return response.getBytes();
	}

	/**
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
	}

	/**
	 * @see com.mnxfst.testing.consumer.handler.IHttpRequestHandler#shutdown()
	 */
	public void shutdown() throws HttpRequestProcessingException {
		running = false;
	}

	/**
	 * @see com.mnxfst.testing.consumer.handler.IHttpRequestHandler#getHandlerStatistics()
	 */
	public HttpRequestHandlerStatistics getHandlerStatistics() {
		return null;
	}

	/**
	 * @see com.mnxfst.testing.consumer.handler.IHttpRequestHandler#getId()
	 */
	public String getId() {
		return id;
	}

	/**
	 * @see com.mnxfst.testing.consumer.handler.IHttpRequestHandler#setId(java.lang.String)
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * @see com.mnxfst.testing.consumer.handler.IHttpRequestHandler#getType()
	 */
	public String getType() {
		return type;
	}

	/**
	 * @see com.mnxfst.testing.consumer.handler.IHttpRequestHandler#setType(java.lang.String)
	 */
	public void setType(String type) {
		this.type = type;
	}

}

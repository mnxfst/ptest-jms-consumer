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

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;

/**
 * Provides a programmatically defined setup for the request handling channel pipeline
 * @author mnxfst
 * @since 20.02.2012
 */
public class JMSConsumerPipelineFactory implements ChannelPipelineFactory {

	private int port = 0;
	
	public JMSConsumerPipelineFactory(int port) {
		this.port = port;
	}
	
	/**
	 * @see org.jboss.netty.channel.ChannelPipelineFactory#getPipeline()
	 */
	public ChannelPipeline getPipeline() throws Exception {
		
		ChannelPipeline channelPipeline = Channels.pipeline();
		
		channelPipeline.addLast("decoder", new HttpRequestDecoder());
		channelPipeline.addLast("aggregator", new HttpChunkAggregator(1048576));
		channelPipeline.addLast("encoder", new HttpResponseEncoder());
		channelPipeline.addLast("deflater", new HttpContentCompressor());
		channelPipeline.addLast("handler", new JMSConsumerHTTPRequestHandler());
		
		return channelPipeline;
		
	}

}

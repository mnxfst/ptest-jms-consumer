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

import java.util.List;
import java.util.Map;

import javax.jms.Message;

import com.mnxfst.testing.consumer.exception.AsyncInputConsumerException;

/**
 * Provides an analyzer for selected messages received from a JMS destination
 * @author mnxfst
 * @since 22.02.2012
 */
public interface IMessageAnalyzer extends Runnable {

	/**
	 * Initializes the analyzer
	 * @param configuration
	 */
	public void initialize(Map<String, List<String>> configuration) throws AsyncInputConsumerException;
	
	/**
	 * Processes the incoming message
	 * @param message
	 */
	public void onMessage(Message message);
	
	/**
	 * Shutsdown the message analyzer
	 */
	public void shutdown();
	
}

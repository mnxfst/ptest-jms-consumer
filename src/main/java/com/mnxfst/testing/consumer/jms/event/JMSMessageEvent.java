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
package com.mnxfst.testing.consumer.jms.event;

import java.io.Serializable;

import com.lmax.disruptor.EventFactory;

/**
 * Simple container
 * @author mnxfst
 *
 */
public class JMSMessageEvent implements Serializable {

	private static final long serialVersionUID = 7075152919216201371L;

	private String messageText = null;
	private long timestamp = 0;
	
	public JMSMessageEvent() {
		
	}
	
	public JMSMessageEvent(String messageText) {
		this.messageText = messageText;
	}

	/**
	 * @return the timestamp
	 */
	public long getTimestamp() {
		return timestamp;
	}

	/**
	 * @param timestamp the timestamp to set
	 */
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	/**
	 * @return the messageText
	 */
	public String getMessageText() {
		return messageText;
	}

	/**
	 * @param messageText the messageText to set
	 */
	public void setMessageText(String messageText) {
		this.messageText = messageText;
	}
	
	public final static EventFactory<JMSMessageEvent> EVENT_FACTORY = new EventFactory<JMSMessageEvent>() {
		
		public JMSMessageEvent newInstance() {
			return new JMSMessageEvent();
		}
	};
	
}

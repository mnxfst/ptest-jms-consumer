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

package com.mnxfst.testing.jms.exception;

/**
 * Thrown in case anything failed during the jms consumer thread start
 * @author mnxfst
 * @since 20.02.2012
 */
public class JMSConsumerStartException extends Exception {

	private static final long serialVersionUID = 4474332222034743189L;

	public JMSConsumerStartException() {		
	}
	
	public JMSConsumerStartException(String msg) {
		super(msg);
	}
	
	public JMSConsumerStartException(Throwable cause) {
		super(cause);
	}
	
	public JMSConsumerStartException(String msg, Throwable cause) {
		super(msg, cause);
	}	

}

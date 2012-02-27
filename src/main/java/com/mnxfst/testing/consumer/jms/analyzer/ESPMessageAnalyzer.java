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

import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.ibm.icu.text.SimpleDateFormat;
import com.ibm.icu.util.TimeZone;
import com.lmax.disruptor.EventHandler;
import com.mnxfst.testing.consumer.exception.AsyncInputConsumerException;
import com.mnxfst.testing.consumer.jms.event.JMSMessageEvent;

/**
 * esp project specifc log analyzer
 * @author mnxfst
 * @since 22.02.2012
 */
public class ESPMessageAnalyzer implements EventHandler<JMSMessageEvent> {

	private static final Logger logger = Logger.getLogger(ESPMessageAnalyzer.class.getName());			

	private static final String CFG_PROP_NODE_ID = "nodeId";
	private static final String CFG_PROP_MEASURING_POINT_ID = "measuringPointId";
	private static final String CFG_PROP_REQUIRED_DOMAIN_SIGN = "requiredDomainSign";

	private static final String REQ_ID_START_TAG = "<pub:Id>";
	private static final int REQ_ID_START_TAG_LENGTH = 8;
	private static final String REQ_ID_END_TAG = "</pub:Id>";
	
	private static final String DOM_SIGN_START_TAG = "<pub:domainSign>";
	private static final int DOM_SIGN_START_TAG_LENGTH = 16;
	private static final String DOM_SIGN_END_TAG = "</pub:domainSign>";
	
	private static final String TITLE_START_TAG = "<pub:title>";
	private static final int TITLE_START_TAG_LENGTH = 11;
	private static final String TITLE_END_TAG = "</pub:title>";

	private static final String MAT_GROUP_START_TAG = "<pub:materialGroup>";
	private static final int MAT_GROUP_START_TAG_LENGTH = 19;
	private static final String MAT_GROUP_END_TAG = "</pub:materialGroup>";

	private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
	
	private String nodeId = null;
	private String measuringPointId = null;
	private String requiredDomainSign = null;
	
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
		else
			this.requiredDomainSign = this.requiredDomainSign.trim();
		
		
		// switch to utc
		dateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
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
	 * @see com.lmax.disruptor.EventHandler#onEvent(java.lang.Object, long, boolean)
	 */
	public void onEvent(JMSMessageEvent event, long sequence, boolean endOfBatch) throws Exception {

		if(event != null && event.getMessageText() != null) {						

			long incomingTime = event.getTimestamp();
			String msg = event.getMessageText();
			
			int reqIdStartIdx = msg.indexOf(REQ_ID_START_TAG);
			int reqIdEndIdx = msg.indexOf(REQ_ID_END_TAG);					
			String requestId = msg.substring(reqIdStartIdx + REQ_ID_START_TAG_LENGTH, reqIdEndIdx);
			
			int domainIdStartIdx = msg.indexOf(DOM_SIGN_START_TAG);
			int domainidEndIdx = msg.indexOf(DOM_SIGN_END_TAG);
			String domainSign = msg.substring(domainIdStartIdx + DOM_SIGN_START_TAG_LENGTH, domainidEndIdx);
			
			int titleIdxStart = msg.indexOf(TITLE_START_TAG);
			int titleIdxEnd = msg.indexOf(TITLE_END_TAG);
			String title = msg.substring(titleIdxStart + TITLE_START_TAG_LENGTH, titleIdxEnd);
			
			int matIdxStart = msg.indexOf(MAT_GROUP_START_TAG);
			int matIdxEnd = msg.indexOf(MAT_GROUP_END_TAG);
			String materialGroup = msg.substring(matIdxStart + MAT_GROUP_START_TAG_LENGTH, matIdxEnd);
		
			boolean validMessage = (requestId != null && !requestId.isEmpty());
			
			if(validMessage)
				validMessage = (domainSign != null && domainSign.trim().equalsIgnoreCase(requiredDomainSign));
			if(validMessage)
				validMessage = (title != null && !title.isEmpty());
			if(validMessage)
				validMessage = (materialGroup != null && !materialGroup.isEmpty());
			
			StringBuffer logBuffer = new StringBuffer();
			logBuffer.append(requestId).append(";").append(nodeId).append(";").append(measuringPointId).append(";").append(incomingTime).append(";").append(dateFormatter.format(incomingTime)).append(";").append("true;").append(validMessage); // TODO validate request
			logger.info(logBuffer.toString());
		}
	}

	/**
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		running = true;
		while(running);
	}
	
	public void shutdown() {
		running = false;
	}

	static String msg = "<pub:PublishProductRequest xmlns:pub=\"http://b2c.otto.de/schema/publishProduct\"><pub:sourceParameter><pub:Id>${global.scenarioId}-${global.productId}-${global.runId}-${global.threads}-${global.waitTime}-${run.requestId}</pub:Id><pub:orderingSystem>Internet</pub:orderingSystem><pub:domainSign>${run.domainSign}</pub:domainSign></pub:sourceParameter><pub:path>//localhost</pub:path><pub:keywords><pub:keyword>Hose</pub:keyword><pub:keyword>lang</pub:keyword></pub:keywords><pub:title>die lange Hose${run.randomInt}</pub:title><pub:descriptionShort>elegante Hose</pub:descriptionShort><pub:descriptionLong>die elegante Hose</pub:descriptionLong><pub:brand>Levis</pub:brand><pub:materialGroup>stoff</pub:materialGroup><pub:categoryList><pub:category>Kleidung</pub:category><pub:category>Mann</pub:category></pub:categoryList><pub:characteristicList><pub:map><pub:value>hose</pub:value><pub:value>fällt klein aus${run.randomInt}</pub:value></pub:map></pub:characteristicList><pub:mediaList><pub:media><pub:media>Video</pub:media></pub:media></pub:mediaList><pub:constraints><pub:map><pub:value>Lieferservice gleich ${run.randomInt} Wochen</pub:value></pub:map></pub:constraints><pub:aktionen><pub:map><pub:value>bekannt aus tv</pub:value></pub:map></pub:aktionen><pub:onlineFrom>2001-10-26T21:32:52</pub:onlineFrom><pub:onlineTo>2010-10-26T21:32:52</pub:onlineTo></pub:PublishProductRequest>";
	
	public static void main(String[] args) {
		long s = 1330097760527L;
		long e = 1330097761038L;
		System.out.println("4000 / " + (e-s) + ": " + (4000/ (e-s)));
		
		s = 1330103192754L;
		e = 1330103584047L;
		System.out.println("128000 / " + (e-s) + ": " + (128000/ (e-s)));
		
		s = 1330103732985L;
		e = 1330103733470L;
		System.out.println("6000 / " + (e-s) + ": " + (6000/ (e-s)));
		
		s = 1330104230754L;
		e = 1330104250260L;
		System.out.println("6000 / " + (e-s) + ": " + (6000/ (e-s)));

		s = 1330116558133L;
		e = 1330116734692L;
		System.out.println("server: 64000 / " + (e-s) + ": " + (64000/ (e-s)));
		
		s = 1330121587250L;
		e = 1330121599968L;
		System.out.println("mock: 64000 / " + (e-s) + ": " + (64000/ (e-s)));
		
		s = 1330122126921L;
		e = 1330122126968L;
		System.out.println((e-s));
		
		s = 1330122126984L;
		e = 1330122126984L;
		System.out.println((e-s));
		
		s = 1330334633382L;
		e = 1330335026324L;
		System.out.println((e-s) + "ms");
		
		s = 1330341530333L;
		e = 1330341533559L;
		System.out.println((e-s) + "ms");

		s = 1330341960768L;
		e = 1330342149822L;
		System.out.println((e-s) + "ms");

		int reqIdStartIdx = msg.indexOf(REQ_ID_START_TAG);
		int reqIdEndIdx = msg.indexOf(REQ_ID_END_TAG);					
		String requestId = msg.substring(reqIdStartIdx + REQ_ID_START_TAG_LENGTH, reqIdEndIdx);
		
		int domainIdStartIdx = msg.indexOf(DOM_SIGN_START_TAG);
		int domainidEndIdx = msg.indexOf(DOM_SIGN_END_TAG);
		String domainSign = msg.substring(domainIdStartIdx + DOM_SIGN_START_TAG_LENGTH, domainidEndIdx);
		
		int titleIdxStart = msg.indexOf(TITLE_START_TAG);
		int titleIdxEnd = msg.indexOf(TITLE_END_TAG);
		String title = msg.substring(titleIdxStart + TITLE_START_TAG_LENGTH, titleIdxEnd);
		
		int matIdxStart = msg.indexOf(MAT_GROUP_START_TAG);
		int matIdxEnd = msg.indexOf(MAT_GROUP_END_TAG);
		String materialGroup = msg.substring(matIdxStart + MAT_GROUP_START_TAG_LENGTH, matIdxEnd);
		
		
		System.out.println(requestId);
		System.out.println(domainSign);
		System.out.println(title);
		System.out.println(materialGroup);
	}
	
}

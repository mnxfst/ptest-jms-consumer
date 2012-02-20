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

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

import com.mnxfst.testing.jms.exception.JMSConsumerConfigException;

/**
 * Ramps up the jms consumer
 * @author mnxfst
 * @since 20.02.2012
 */
public class JMSConsumerMain {

	public static final String CMD_OPT_PORT = "port";
	public static final String CMD_OPT_PORT_SHORT = "p";
	public static final String CMD_OPT_THREAD_POOL_SIZE = "poolSize";
	public static final String CMD_OPT_THREAD_POOL_SIZE_SHORT = "ps";

	
	/** 
	 * Starts the consumer
	 */
	public static void main(String[] args) {
		
		args = new String[]{"-p", "9090"};
		
		new JMSConsumerMain().execute(args);
		
	}
	
	/**
	 * Executes the server instance using the provided parameter as configuration
	 * @param args
	 */
	public void execute(String[] args) {
		Options commandLineOptions = getCommandLineOptions();
		CommandLine commandLine = null;
		try {
			commandLine = parseCommandline(commandLineOptions, args);
		} catch(ParseException e) {
			System.out.println("Failed to parse command-line");
		}

		int port = -1;
		try {
			port = extractIntValue(commandLine, CMD_OPT_PORT, CMD_OPT_PORT_SHORT);
		} catch (JMSConsumerConfigException e) {
			printHelp(commandLineOptions, "Failed to parse port from command-line");
			return;
		}

		if(port < 1) {
			printHelp(commandLineOptions, "Failed to parse port from command-line");
			return;
		}

		int threadPoolSize = -1;
		try {
			threadPoolSize = extractIntValue(commandLine, CMD_OPT_THREAD_POOL_SIZE, CMD_OPT_THREAD_POOL_SIZE_SHORT);
		} catch(JMSConsumerConfigException e) {
			threadPoolSize = -1;
		}

		
		ChannelFactory channelFactory = null;
		if(threadPoolSize > 0)
			channelFactory = new NioServerSocketChannelFactory(Executors.newFixedThreadPool(threadPoolSize), Executors.newFixedThreadPool(threadPoolSize));
		else
			channelFactory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());
		
		ServerBootstrap serverBootstrap = new ServerBootstrap(channelFactory);
		serverBootstrap.setPipelineFactory(new JMSConsumerPipelineFactory(port));
		serverBootstrap.setOption("child.tcpNoDelay", true);
		serverBootstrap.setOption("child.keepAlive", true);
		
		serverBootstrap.bind(new InetSocketAddress(port));
		
	}
	
	protected Options getCommandLineOptions()  {
		Options options = new Options();
		options.addOption(CMD_OPT_PORT_SHORT, CMD_OPT_PORT, true, "Port to be used for setting up communication");
		options.addOption(CMD_OPT_THREAD_POOL_SIZE_SHORT, CMD_OPT_THREAD_POOL_SIZE, true, "Size used for setting up the server socket thread pool (optional)");
		return options;				
	}
	
	/**
	 * Parses the provided command-line against the given set of options using the posix compatible parser
	 * @param options
	 * @param commandLineArguments
	 * @return
	 * @throws ParseException
	 */
	public CommandLine parseCommandline(Options options, String[] commandLineArguments) throws ParseException {
		CommandLineParser parser = new PosixParser();
		return parser.parse(options, commandLineArguments);		
	}

	/**
	 * Prints out the command-line help 
	 * @param options
	 */
	public void printHelp(Options options, String additionalMessage) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp( JMSConsumerMain.class.getName(), options );
		
		if(additionalMessage != null && !additionalMessage.isEmpty())
			System.out.println("\n" + additionalMessage);
		
	}

	/**
	 * Extracts a long value from the named command-line option
	 * @param cmd
	 * @param opt
	 * @param shortOpt
	 * @return
	 * @throws TSClientConfigurationException
	 */
	public int extractIntValue(CommandLine cmd, String opt, String shortOpt) throws JMSConsumerConfigException {		
		String tmp = cmd.getOptionValue(opt);
		if(tmp == null || tmp.isEmpty())
			tmp = cmd.getOptionValue(shortOpt);
		if(tmp == null || tmp.isEmpty())
			throw new JMSConsumerConfigException("Missing value for required option '"+opt+"' ('"+shortOpt+"')");
		
		try {
			return Integer.parseInt(tmp.trim());
		} catch(NumberFormatException e) {
			throw new JMSConsumerConfigException("Value for required option '"+opt+"' ('"+shortOpt+"') does not represent a valid numerical value: " + tmp);
		}		
	}
	
	
}

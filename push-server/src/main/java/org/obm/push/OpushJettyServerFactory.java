/* ***** BEGIN LICENSE BLOCK *****
 * Copyright (C) 2011-2014  Linagora
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version, provided you comply with the Additional Terms applicable for OBM
 * software by Linagora pursuant to Section 7 of the GNU Affero General Public
 * License, subsections (b), (c), and (e), pursuant to which you must notably (i)
 * retain the displaying by the interactive user interfaces of the “OBM, Free
 * Communication by Linagora” Logo with the “You are using the Open Source and
 * free version of OBM developed and supported by Linagora. Contribute to OBM R&D
 * by subscribing to an Enterprise offer !” infobox, (ii) retain all hypertext
 * links between OBM and obm.org, between Linagora and linagora.com, as well as
 * between the expression “Enterprise offer” and pro.obm.org, and (iii) refrain
 * from infringing Linagora intellectual property rights over its trademarks and
 * commercial brands. Other Additional Terms apply, see
 * <http://www.linagora.com/licenses/> for more details.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License and
 * its applicable Additional Terms for OBM along with this program. If not, see
 * <http://www.gnu.org/licenses/> for the GNU Affero General   Public License
 * version 3 and <http://www.linagora.com/licenses/> for the Additional Terms
 * applicable to the OBM software.
 * ***** END LICENSE BLOCK ***** */
package org.obm.push;

import java.util.EnumSet;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import javax.servlet.DispatcherType;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.component.LifeCycle.Listener;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.Scheduler;
import org.obm.push.configuration.LoggerModule;
import org.obm.push.configuration.OpushConfiguration;
import org.obm.sync.LifecycleListenerHelper;
import org.slf4j.Logger;

import ch.qos.logback.access.jetty.RequestLogImpl;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;
import com.google.inject.servlet.GuiceFilter;

public class OpushJettyServerFactory {

	private static final long GRACEFUL_STOP_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(1);
	private static final Executor DEFAULT_EXECUTOR = null;
	private static final Scheduler DEFAULT_SCHEDULER = null;
	private static final ByteBufferPool DEFAULT_BUFFERPOOL_OBJECT = null;
	private static final int DEFAULT_ACCEPTOR_COUNT = -1;

	private final Injector injector;
	private final OpushConfiguration configuration;
	private final Logger logger;

	@Inject
	protected OpushJettyServerFactory(Injector injector, 
			OpushConfiguration configuration,
			@Named(LoggerModule.CONTAINER) Logger logger) {
		this.injector = injector;
		this.configuration = configuration;
		this.logger = logger;
	}
	
	public OpushServer buildServer(int port, int threadPoolSize, int selectorCount) {
		final Server jetty = new Server(new QueuedThreadPool(threadPoolSize));
		jetty.setStopAtShutdown(true);
		jetty.setStopTimeout(GRACEFUL_STOP_TIMEOUT_MS);
		
		final ServerConnector httpConnector = new ServerConnector(jetty, 
				DEFAULT_EXECUTOR, DEFAULT_SCHEDULER, DEFAULT_BUFFERPOOL_OBJECT, DEFAULT_ACCEPTOR_COUNT, selectorCount, new HttpConnectionFactory());
		httpConnector.setPort(port);
		jetty.addConnector(httpConnector);
		jetty.setHandler(buildHandlers(injector, configuration, logger));

		return new OpushServer() {
			
			@Override
			public void stop() throws Exception {
				jetty.stop();
			}
			
			@Override
			public void start() throws Exception {
				jetty.start();
			}
			
			@Override
			public void join() throws Exception {
				jetty.join();
			}

			@Override
			public int getHttpPort() {
				if (jetty.isRunning()) {
					return httpConnector.getLocalPort();
				}
				throw new IllegalStateException("Could not get server's listening port. Start the server first.");
			}
		};
	}

	private Handler buildHandlers(Injector injector, OpushConfiguration configuration, Logger logger) {
		HandlerCollection handlers = new HandlerCollection();
		handlers.addLifeCycleListener(buildLifeCycleListener(logger));
		handlers.addHandler(buildServletContext(injector));
		handlers.addHandler(buildRequestLogger(configuration));
		return handlers;
	}

	private RequestLogHandler buildRequestLogger(OpushConfiguration configuration) {
		RequestLogHandler requestLogHandler = new RequestLogHandler();
		if (configuration.isRequestLoggerEnabled()) {
			RequestLogImpl requestLog = new RequestLogImpl();
			requestLog.setResource("/logback-access.xml");
			requestLogHandler.setRequestLog(requestLog);
		}
		return requestLogHandler;
	}
	
	private ServletContextHandler buildServletContext(Injector injector) {
		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath("/opush/");
		context.addFilter(new FilterHolder(injector.getInstance(GuiceFilter.class)), "/*", EnumSet.allOf(DispatcherType.class));
		context.addServlet(DefaultServlet.class, "/");
		context.addEventListener(buildCleanupListener(injector));
		return context;
	}

	private ServletContextListener buildCleanupListener(final Injector injector) {
		return new ServletContextListener() {

			@Override
			public void contextInitialized(ServletContextEvent sce) {
			}

			@Override
			public void contextDestroyed(ServletContextEvent sce) {
				LifecycleListenerHelper.shutdownListeners(injector);
			}
		};
	}

	private Listener buildLifeCycleListener(final Logger logger) {
		return new Listener() {
			@Override
			public void lifeCycleStopping(LifeCycle event) {
				logger.info("Application stopping");
			}
			@Override
			public void lifeCycleStopped(LifeCycle event) {
				logger.info("Application stopped");
			}
			@Override
			public void lifeCycleStarting(LifeCycle event) {
				logger.info("Application starting");
			}
			@Override
			public void lifeCycleStarted(LifeCycle event) {
				logger.info("Application started");
			}
			@Override
			public void lifeCycleFailure(LifeCycle event, Throwable cause) {
				logger.error("Application failure", cause);
			}
		};
	}
}

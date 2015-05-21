/**
 * Copyright (c) 2007-2014 Kaazing Corporation. All rights reserved.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.kaazing.gateway.transport.http;

import org.apache.mina.core.future.CloseFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.kaazing.gateway.resource.address.http.HttpResourceAddress;
import org.kaazing.gateway.transport.http.bridge.filter.HttpFilterAdapter;
import org.kaazing.mina.core.session.IoSessionEx;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/*
 * A pool for reusable persistent transport connections. HttpConnector
 * may pick one of the transport connections instead of creating a new
 * one while connecting to the origin server.
 */
public class PersistentConnectionPool {

    private static final String IDLE_FILTER = HttpProtocol.NAME + "#idle";

    // transport address -> set of persistent connections
    private final Map<HttpResourceAddress, Set<IoSession>> connections;
    private final Logger logger;
    private final HttpConnectIdleFilter idleFilter;

    PersistentConnectionPool(Logger logger) {
        this.connections = new HashMap<>();
        this.logger = logger;
        this.idleFilter = new HttpConnectIdleFilter(logger);
    }

    /*
     * Recycle existing transport session so that it can be used as a http
     * persistent connection
     *
     * @return true if the idle connection is cached for reuse
     *         false otherwise
     */
    public boolean recycle(DefaultHttpSession httpSession) {
        if (!add(httpSession)) {
            return false;
        }
        if (logger.isDebugEnabled()) {
            logger.debug(String.format("Caching persistent connection: http address=%s, transport session=%s",
                    httpSession.getRemoteAddress(), httpSession.getParent()));
        }
        HttpResourceAddress serverAddress = (HttpResourceAddress)httpSession.getRemoteAddress();
        IoSession transportSession = httpSession.getParent();

        // Take care of transport session close
        CloseFuture closeFuture = transportSession.getCloseFuture();
        closeFuture.addListener(new CloseListener(this, serverAddress, logger));

        // Track transport session idle
        transportSession.getFilterChain().addLast(IDLE_FILTER, idleFilter);
        int keepAliveTimeout = serverAddress.getOption(HttpResourceAddress.KEEP_ALIVE_TIMEOUT);
        transportSession.getConfig().setBothIdleTime(keepAliveTimeout);

        return true;
    }

    /*
     * Returns an existing transport session for the resource address that can be reused
     *
     * @return a reusable IoSession for the address
     *         otherwise null
     */
    public IoSession take(HttpResourceAddress serverAddress) {
        IoSession transportSession = removeThreadLocal(serverAddress);
        if (transportSession != null) {
            if (logger.isDebugEnabled()) {
                logger.debug(String.format("Reusing cached persistent connection: http address=%s, transport session=%s",
                        serverAddress, transportSession));
            }
            transportSession.getConfig().setBothIdleTime(0);
            transportSession.getFilterChain().remove(IDLE_FILTER);
        }

        return transportSession;
    }



    /*
     * If a session is closed, it will be removed from this pool using this
     * CloseFuture listener
     */
    private static class CloseListener implements IoFutureListener<CloseFuture> {

        private final PersistentConnectionPool store;
        private final HttpResourceAddress serverAddress;
        private final Logger logger;

        CloseListener(PersistentConnectionPool store, HttpResourceAddress serverAddress, Logger logger) {
            this.store = store;
            this.serverAddress = serverAddress;
            this.logger = logger;
        }

        @Override
        public void operationComplete(CloseFuture future) {
            IoSessionEx session = (IoSessionEx) future.getSession();
            store.remove(serverAddress, session);
            if (logger.isDebugEnabled()) {
                logger.debug(String.format("Removed cached persistent connection: http address=%s, transport session=%s",
                        serverAddress, session));
            }
        }
    }

    /*
     * Filter to detect if a persistent connection is idle
     */
    private static class HttpConnectIdleFilter extends HttpFilterAdapter<IoSessionEx> {
        private final Logger logger;

        HttpConnectIdleFilter(Logger logger) {
            this.logger = logger;
        }

        @Override
        public void sessionIdle(NextFilter nextFilter, IoSession session, IdleStatus status) throws Exception {
            if (logger.isDebugEnabled()) {
                logger.debug(String.format("Idle cached persistent connection: transport session=%s", session));
            }
            session.getConfig().setBothIdleTime(0);
            session.getFilterChain().remove(IDLE_FILTER);

            // Transport connection will be removed from pool in an listener of CloseFuture
            session.close(false);
        }
    }

    private boolean add(DefaultHttpSession httpSession) {
        HttpResourceAddress serverAddress = (HttpResourceAddress)httpSession.getRemoteAddress();
        int max = serverAddress.getOption(HttpResourceAddress.KEEP_ALIVE_MAX_CONNECTIONS);
        IoSession transportSession = httpSession.getParent();

        synchronized (connections) {
            Set<IoSession> transportSessions = connections.get(serverAddress);
            if (transportSessions == null) {
                transportSessions = new HashSet<>();
                connections.put(serverAddress, transportSessions);
            }
            if (transportSessions.size() < max) {
                transportSessions.add(transportSession);
                return true;
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug(String.format("Not caching persistent connection: http address=%s, transport session=%s",
                    serverAddress, transportSession));
        }
        return false;
    }

    /*
     * Returns an existing transport session for the given resource address
     * that can be reused
     *
     * @return a reusable IoSession for the address
     *         otherwise null
     */
    private void remove(HttpResourceAddress serverAddress, IoSession session) {
        synchronized (connections) {
            Set<IoSession> transportSessions = connections.get(serverAddress);
            transportSessions.remove(session);
        }
    }

    private IoSession removeThreadLocal(HttpResourceAddress serverAddress) {
        synchronized (connections) {
            Set<IoSession> transportSessions = connections.get(serverAddress);
            if (transportSessions != null) {
                for (IoSession transportSession : transportSessions) {
                    if (((IoSessionEx) transportSession).getIoThread() == Thread.currentThread()) {
                        transportSessions.remove(transportSession);
                        return transportSession;
                    }
                }
            }
        }
        return null;
    }

}

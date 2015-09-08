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

package org.kaazing.gateway.management.monitoring.writer.impl;

import org.kaazing.gateway.management.monitoring.configuration.MonitorFileWriter;
import org.kaazing.gateway.management.monitoring.entity.impl.AgronaMonitoringEntityFactory;
import org.kaazing.gateway.management.monitoring.writer.GatewayWriter;
import org.kaazing.gateway.service.MonitoringEntityFactory;

import uk.co.real_logic.agrona.concurrent.CountersManager;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class MMFGatewayWriter implements GatewayWriter {

    private CountersManager countersManager;
    private MonitorFileWriter monitorFileWriter;

    public MMFGatewayWriter(MonitorFileWriter monitorFile) {
        this.monitorFileWriter = monitorFile;
    }

    @Override
    public MonitoringEntityFactory writeCountersFactory() {
        createCountersManager();
        MonitoringEntityFactory factory = new AgronaMonitoringEntityFactory(countersManager);
        return factory;
    }

    /**
     * Helper method instantiating a counters manager
     */
    private void createCountersManager() {
        UnsafeBuffer counterLabelsBuffer = monitorFileWriter.createGatewayCounterLabelsBuffer();
        UnsafeBuffer counterValuesBuffer = monitorFileWriter.createGatewayCounterValuesBuffer();

        countersManager = new CountersManager(counterLabelsBuffer, counterValuesBuffer);
    }

}

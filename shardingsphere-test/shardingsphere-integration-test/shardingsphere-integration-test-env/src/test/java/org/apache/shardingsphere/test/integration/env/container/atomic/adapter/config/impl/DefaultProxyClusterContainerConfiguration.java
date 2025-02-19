/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.test.integration.env.container.atomic.adapter.config.impl;

import org.apache.shardingsphere.infra.database.type.DatabaseType;
import org.apache.shardingsphere.test.integration.env.container.atomic.adapter.config.AdaptorContainerConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Proxy cluster container configuration.
 */
public final class DefaultProxyClusterContainerConfiguration implements AdaptorContainerConfiguration {
    
    @Override
    public Map<String, String> getWaitStrategyInfo() {
        return Collections.singletonMap("dataSourceName", "");
    }
    
    @Override
    public Map<String, String> getResourceMappings(final String scenario, final DatabaseType databaseType) {
        Map<String, String> result = new HashMap<>(2, 1);
        String pathInContainer = "/opt/shardingsphere-proxy/conf";
        result.put("/env/common/standalone/proxy/conf/", pathInContainer);
        result.put("/env/scenario/" + scenario + "/proxy/conf/" + databaseType.getType().toLowerCase(), pathInContainer);
        return result;
    }
}

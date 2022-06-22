/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.core.base.config;

import org.apache.seatunnel.shade.com.typesafe.config.Config;
import org.apache.seatunnel.shade.com.typesafe.config.ConfigFactory;
import org.apache.seatunnel.shade.com.typesafe.config.ConfigResolveOptions;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Map;
import java.util.stream.Collectors;

public class ConfigParser {

    public static Map<String, String> getConfigEnvValues(String configFile) throws FileNotFoundException {
        File file = new File(configFile);
        if (!file.exists()) {
            throw new FileNotFoundException("config file '" + file + "' does not exists!");
        }
        Config appConfig = ConfigFactory.parseFile(file)
            .resolve(ConfigResolveOptions.defaults().setAllowUnresolved(true))
            .resolveWith(ConfigFactory.systemProperties(), ConfigResolveOptions.defaults().setAllowUnresolved(true));

        return appConfig.getConfig("env")
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().unwrapped().toString()));
    }
}

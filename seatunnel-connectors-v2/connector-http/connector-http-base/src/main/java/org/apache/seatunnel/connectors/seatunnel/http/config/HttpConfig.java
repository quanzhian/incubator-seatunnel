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

package org.apache.seatunnel.connectors.seatunnel.http.config;

public class HttpConfig {
    public static final String URL = "url";
    public static final String METHOD = "method";
    public static final String METHOD_DEFAULT_VALUE = "GET";
    public static final String HEADERS = "headers";
    public static final String PARAMS = "params";
    public static final String BODY = "body";
    public static final String SCHEMA = "schema";
    public static final String FORMAT = "format";
    public static final String DEFAULT_FORMAT = "json";
    public static final String POLL_INTERVAL_MILLS = "poll_interval_ms";
    public static final String RETRY = "retry";
    public static final String RETRY_BACKOFF_MULTIPLIER_MS = "retry_backoff_multiplier_ms";
    public static final String RETRY_BACKOFF_MAX_MS = "retry_backoff_max_ms";
}

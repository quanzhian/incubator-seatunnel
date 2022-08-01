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

package org.apache.seatunnel.connectors.seatunnel.console.sink;

import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.connectors.seatunnel.common.sink.AbstractSinkWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class ConsoleSinkWriter extends AbstractSinkWriter<SeaTunnelRow, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleSinkWriter.class);

    private final SeaTunnelRowType seaTunnelRowType;

    public ConsoleSinkWriter(SeaTunnelRowType seaTunnelRowType) {
        this.seaTunnelRowType = seaTunnelRowType;
    }

    @Override
    @SuppressWarnings("checkstyle:RegexpSingleline")
    public void write(SeaTunnelRow element) {
        System.out.println(Arrays.toString(element.getFields()));
    }

    @Override
    public void close() {
        // nothing
    }
}

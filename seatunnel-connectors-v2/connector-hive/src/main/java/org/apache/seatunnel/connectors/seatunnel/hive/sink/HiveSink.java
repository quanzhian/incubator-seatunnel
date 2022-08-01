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

package org.apache.seatunnel.connectors.seatunnel.hive.sink;

import org.apache.seatunnel.api.common.PrepareFailException;
import org.apache.seatunnel.api.common.SeaTunnelContext;
import org.apache.seatunnel.api.serialization.DefaultSerializer;
import org.apache.seatunnel.api.serialization.Serializer;
import org.apache.seatunnel.api.sink.SeaTunnelSink;
import org.apache.seatunnel.api.sink.SinkAggregatedCommitter;
import org.apache.seatunnel.api.sink.SinkWriter;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.common.constants.JobMode;
import org.apache.seatunnel.connectors.seatunnel.file.sink.config.SaveMode;

import org.apache.seatunnel.shade.com.typesafe.config.Config;

import com.google.auto.service.AutoService;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Hive Sink implementation by using SeaTunnel sink API.
 * This class contains the method to create {@link HiveSinkWriter} and {@link HiveSinkAggregatedCommitter}.
 */
@AutoService(SeaTunnelSink.class)
public class HiveSink implements SeaTunnelSink<SeaTunnelRow, HiveSinkState, HiveCommitInfo, HiveAggregatedCommitInfo> {

    private Config config;
    private String jobId;
    private Long checkpointId;
    private SeaTunnelRowType seaTunnelRowTypeInfo;
    private SeaTunnelContext seaTunnelContext;
    private HiveSinkConfig hiveSinkConfig;

    @Override
    public String getPluginName() {
        return "Hive";
    }

    @Override
    public void setTypeInfo(SeaTunnelRowType seaTunnelRowTypeInfo) {
        this.seaTunnelRowTypeInfo = seaTunnelRowTypeInfo;
        this.hiveSinkConfig = new HiveSinkConfig(config, seaTunnelRowTypeInfo);
    }

    @Override
    public SeaTunnelDataType<SeaTunnelRow> getConsumedType() {
        return this.seaTunnelRowTypeInfo;
    }

    @Override
    public void prepare(Config pluginConfig) throws PrepareFailException {
        this.config = pluginConfig;
        this.checkpointId = 1L;
    }

    @Override
    public SinkWriter<SeaTunnelRow, HiveCommitInfo, HiveSinkState> createWriter(SinkWriter.Context context) throws IOException {
        if (!seaTunnelContext.getJobMode().equals(JobMode.BATCH) && hiveSinkConfig.getTextFileSinkConfig().getSaveMode().equals(SaveMode.OVERWRITE)) {
            throw new RuntimeException("only batch job can overwrite hive table");
        }

        if (!this.getSinkConfig().getTextFileSinkConfig().isEnableTransaction()) {
            throw new RuntimeException("Hive Sink Connector only support transaction now");
        }
        return new HiveSinkWriter(seaTunnelRowTypeInfo,
            config,
            context,
            getSinkConfig(),
            jobId);
    }

    @Override
    public SinkWriter<SeaTunnelRow, HiveCommitInfo, HiveSinkState> restoreWriter(SinkWriter.Context context, List<HiveSinkState> states) throws IOException {
        return new HiveSinkWriter(seaTunnelRowTypeInfo, config, context, hiveSinkConfig, jobId, states);
    }

    @Override
    public void setSeaTunnelContext(SeaTunnelContext seaTunnelContext) {
        this.seaTunnelContext = seaTunnelContext;
        this.jobId = seaTunnelContext.getJobId();
    }

    @Override
    public Optional<SinkAggregatedCommitter<HiveCommitInfo, HiveAggregatedCommitInfo>> createAggregatedCommitter() throws IOException {
        return Optional.of(new HiveSinkAggregatedCommitter());
    }

    @Override
    public Optional<Serializer<HiveSinkState>> getWriterStateSerializer() {
        return Optional.of(new DefaultSerializer<>());
    }

    @Override
    public Optional<Serializer<HiveAggregatedCommitInfo>> getAggregatedCommitInfoSerializer() {
        return Optional.of(new DefaultSerializer<>());
    }

    @Override
    public Optional<Serializer<HiveCommitInfo>> getCommitInfoSerializer() {
        return Optional.of(new DefaultSerializer<>());
    }

    private HiveSinkConfig getSinkConfig() {
        if (this.hiveSinkConfig == null && (this.seaTunnelRowTypeInfo != null && this.config != null)) {
            this.hiveSinkConfig = new HiveSinkConfig(config, seaTunnelRowTypeInfo);
        }
        return this.hiveSinkConfig;
    }
}

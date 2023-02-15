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

package org.apache.seatunnel.engine.client;

import org.apache.seatunnel.shade.com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.seatunnel.shade.com.fasterxml.jackson.databind.JsonNode;
import org.apache.seatunnel.shade.com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.seatunnel.engine.client.job.JobClient;
import org.apache.seatunnel.engine.client.job.JobExecutionEnvironment;
import org.apache.seatunnel.engine.client.job.JobMetricsRunner.JobMetricsSummary;
import org.apache.seatunnel.engine.common.config.JobConfig;
import org.apache.seatunnel.engine.common.utils.PassiveCompletableFuture;
import org.apache.seatunnel.engine.core.job.JobDAGInfo;
import org.apache.seatunnel.engine.core.job.JobStatus;
import org.apache.seatunnel.engine.core.protocol.codec.SeaTunnelCancelJobCodec;
import org.apache.seatunnel.engine.core.protocol.codec.SeaTunnelGetJobDetailStatusCodec;
import org.apache.seatunnel.engine.core.protocol.codec.SeaTunnelGetJobInfoCodec;
import org.apache.seatunnel.engine.core.protocol.codec.SeaTunnelGetJobMetricsCodec;
import org.apache.seatunnel.engine.core.protocol.codec.SeaTunnelGetJobStatusCodec;
import org.apache.seatunnel.engine.core.protocol.codec.SeaTunnelListJobStatusCodec;
import org.apache.seatunnel.engine.core.protocol.codec.SeaTunnelPrintMessageCodec;
import org.apache.seatunnel.engine.core.protocol.codec.SeaTunnelSavePointJobCodec;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.logging.ILogger;
import lombok.NonNull;

public class SeaTunnelClient implements SeaTunnelClientInstance {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final SeaTunnelHazelcastClient hazelcastClient;

    public SeaTunnelClient(@NonNull ClientConfig clientConfig) {
        this.hazelcastClient = new SeaTunnelHazelcastClient(clientConfig);
    }

    @Override
    public JobExecutionEnvironment createExecutionContext(
            @NonNull String filePath, @NonNull JobConfig jobConfig) {
        return new JobExecutionEnvironment(jobConfig, filePath, hazelcastClient);
    }

    @Override
    public JobExecutionEnvironment restoreExecutionContext(
            @NonNull String filePath, @NonNull JobConfig jobConfig, @NonNull Long jobId) {
        return new JobExecutionEnvironment(jobConfig, filePath, hazelcastClient, true, jobId);
    }

    @Override
    public JobClient createJobClient() {
        return new JobClient(hazelcastClient);
    }

    @Override
    public void close() {
        hazelcastClient.getHazelcastInstance().shutdown();
    }

    public ILogger getLogger() {
        return hazelcastClient.getLogger(getClass());
    }

    public String printMessageToMaster(@NonNull String msg) {
        return hazelcastClient.requestOnMasterAndDecodeResponse(
                SeaTunnelPrintMessageCodec.encodeRequest(msg),
                SeaTunnelPrintMessageCodec::decodeResponse);
    }

    public void shutdown() {
        hazelcastClient.shutdown();
    }

    /**
     * get job status and the tasks status
     *
     * @param jobId jobId
     */
    public String getJobDetailStatus(Long jobId) {
        return hazelcastClient.requestOnMasterAndDecodeResponse(
                SeaTunnelGetJobDetailStatusCodec.encodeRequest(jobId),
                SeaTunnelGetJobDetailStatusCodec::decodeResponse);
    }

    /** list all jobId and job status */
    public String listJobStatus() {
        return hazelcastClient.requestOnMasterAndDecodeResponse(
                SeaTunnelListJobStatusCodec.encodeRequest(),
                SeaTunnelListJobStatusCodec::decodeResponse);
    }

    /**
     * get one job status
     *
     * @param jobId jobId
     */
    public String getJobStatus(Long jobId) {
        int jobStatusOrdinal =
                hazelcastClient.requestOnMasterAndDecodeResponse(
                        SeaTunnelGetJobStatusCodec.encodeRequest(jobId),
                        SeaTunnelGetJobStatusCodec::decodeResponse);
        return JobStatus.values()[jobStatusOrdinal].toString();
    }

    public String getJobMetrics(Long jobId) {
        return hazelcastClient.requestOnMasterAndDecodeResponse(
                SeaTunnelGetJobMetricsCodec.encodeRequest(jobId),
                SeaTunnelGetJobMetricsCodec::decodeResponse);
    }

    public void savePointJob(Long jobId) {
        PassiveCompletableFuture<Void> cancelFuture =
                hazelcastClient.requestOnMasterAndGetCompletableFuture(
                        SeaTunnelSavePointJobCodec.encodeRequest(jobId));

        cancelFuture.join();
    }

    public void cancelJob(Long jobId) {
        PassiveCompletableFuture<Void> cancelFuture =
                hazelcastClient.requestOnMasterAndGetCompletableFuture(
                        SeaTunnelCancelJobCodec.encodeRequest(jobId));

        cancelFuture.join();
    }

    public JobDAGInfo getJobInfo(Long jobId) {
        return hazelcastClient
                .getSerializationService()
                .toObject(
                        hazelcastClient.requestOnMasterAndDecodeResponse(
                                SeaTunnelGetJobInfoCodec.encodeRequest(jobId),
                                SeaTunnelGetJobInfoCodec::decodeResponse));
    }

    public JobMetricsSummary getJobMetricsSummary(Long jobId) {
        long sourceReadCount = 0L;
        long sinkWriteCount = 0L;
        String jobMetrics = getJobMetrics(jobId);
        try {
            JsonNode jsonNode = OBJECT_MAPPER.readTree(jobMetrics);
            JsonNode sourceReaders = jsonNode.get("SourceReceivedCount");
            JsonNode sinkWriters = jsonNode.get("SinkWriteCount");
            for (int i = 0; i < sourceReaders.size(); i++) {
                JsonNode sourceReader = sourceReaders.get(i);
                JsonNode sinkWriter = sinkWriters.get(i);
                sourceReadCount += sourceReader.get("value").asLong();
                sinkWriteCount += sinkWriter.get("value").asLong();
            }
            return new JobMetricsSummary(sourceReadCount, sinkWriteCount);
            // Add NullPointerException because of metrics information can be empty like {}
        } catch (JsonProcessingException | NullPointerException e) {
            return new JobMetricsSummary(sourceReadCount, sinkWriteCount);
        }
    }
}

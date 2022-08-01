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

package org.apache.seatunnel.translation.spark.source.continnous;

import org.apache.seatunnel.api.source.SeaTunnelSource;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.common.utils.SerializationUtils;
import org.apache.seatunnel.translation.spark.common.ReaderState;
import org.apache.seatunnel.translation.spark.common.source.continnous.CoordinationState;
import org.apache.seatunnel.translation.spark.common.utils.TypeConverterUtils;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.sources.v2.reader.InputPartition;
import org.apache.spark.sql.sources.v2.reader.streaming.ContinuousReader;
import org.apache.spark.sql.sources.v2.reader.streaming.Offset;
import org.apache.spark.sql.sources.v2.reader.streaming.PartitionOffset;
import org.apache.spark.sql.types.StructType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ContinuousSourceReader implements ContinuousReader {

    private final SeaTunnelSource<SeaTunnelRow, ?, ?> source;
    private final Integer parallelism;
    private final Map<Integer, ReaderState> readerStateMap = new HashMap<>();
    private CoordinationState coordinationState;
    private int checkpointId = 1;

    public ContinuousSourceReader(SeaTunnelSource<SeaTunnelRow, ?, ?> source, Integer parallelism) {
        this.source = source;
        this.parallelism = parallelism;
        throw new UnsupportedOperationException("Continuous source is not currently supported.");
    }

    @Override
    public Offset mergeOffsets(PartitionOffset[] subtaskStates) {
        // aggregate state
        List<ReaderState> readerStateList = new ArrayList<>(subtaskStates.length);
        for (PartitionOffset subtaskState : subtaskStates) {
            if (subtaskState instanceof ReaderState) {
                ReaderState readerState = (ReaderState) subtaskState;
                readerStateMap.put(readerState.getSubtaskId(), readerState);
                readerStateList.add(readerState);
            } else {
                throw new UnsupportedOperationException(String.format("Unsupported state type: %s", subtaskState.getClass()));
            }
        }
        return new CoordinationState(readerStateList, readerStateList.get(0).getCheckpointId());
    }

    @Override
    public Offset deserializeOffset(String aggregatedState) {
        return SerializationUtils.stringToObject(aggregatedState);
    }

    @Override
    public void setStartOffset(Optional<Offset> start) {
        // initialize or restore state
        start.ifPresent(state -> {
            CoordinationState restoreState = (CoordinationState) state;
            checkpointId = restoreState.getCheckpointId();
            for (ReaderState readerState : restoreState.getReaderStateList()) {
                readerStateMap.put(readerState.getSubtaskId(), readerState);
            }
        });
        coordinationState = (CoordinationState) start.orElse(new CoordinationState(new ArrayList<>(), 1));
    }

    @Override
    public Offset getStartOffset() {
        return coordinationState;
    }

    @Override
    public void commit(Offset end) {
        // TODO: rpc commit {@link ContinuousPartitionReader#notifyCheckpointComplete}
    }

    @Override
    public void stop() {
        // TODO: stop rpc
    }

    @Override
    public StructType readSchema() {
        return (StructType) TypeConverterUtils.convert(source.getProducedType());
    }

    @Override
    public List<InputPartition<InternalRow>> planInputPartitions() {
        List<InputPartition<InternalRow>> virtualPartitions = new ArrayList<>(parallelism);
        for (int subtaskId = 0; subtaskId < parallelism; subtaskId++) {
            ReaderState readerState = readerStateMap.get(subtaskId);
            virtualPartitions.add(new ContinuousPartition(source, parallelism, subtaskId, checkpointId, readerState == null ? null : readerState.getBytes()));
        }
        return virtualPartitions;
    }
}

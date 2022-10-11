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

package org.apache.seatunnel.engine.server.service.slot;

import org.apache.seatunnel.common.utils.RetryUtils;
import org.apache.seatunnel.engine.common.utils.IdGenerator;
import org.apache.seatunnel.engine.server.SeaTunnelServer;
import org.apache.seatunnel.engine.server.TaskExecutionService;
import org.apache.seatunnel.engine.server.resourcemanager.opeartion.WorkerHeartbeatOperation;
import org.apache.seatunnel.engine.server.resourcemanager.resource.CPU;
import org.apache.seatunnel.engine.server.resourcemanager.resource.Memory;
import org.apache.seatunnel.engine.server.resourcemanager.resource.ResourceProfile;
import org.apache.seatunnel.engine.server.resourcemanager.resource.SlotProfile;
import org.apache.seatunnel.engine.server.resourcemanager.worker.WorkerProfile;

import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.spi.impl.NodeEngineImpl;
import com.hazelcast.spi.impl.operationservice.InvocationBuilder;
import com.hazelcast.spi.impl.operationservice.Operation;
import com.hazelcast.spi.impl.operationservice.impl.InvocationFuture;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The slot service of seatunnel server, used for manage slot in worker.
 */
public class DefaultSlotService implements SlotService {

    private static final ILogger LOGGER = Logger.getLogger(DefaultSlotService.class);
    private static final long DEFAULT_HEARTBEAT_TIMEOUT = 2000;
    private static final int HEARTBEAT_RETRY_TIME = 5;
    private final NodeEngineImpl nodeEngine;

    private AtomicReference<ResourceProfile> unassignedResource;

    private AtomicReference<ResourceProfile> assignedResource;

    private ConcurrentMap<Integer, SlotProfile> assignedSlots;

    private ConcurrentMap<Integer, SlotProfile> unassignedSlots;
    private ScheduledExecutorService scheduledExecutorService;
    private final boolean dynamicSlot;
    private final int slotNumber;
    private volatile boolean initStatus;
    private final IdGenerator idGenerator;
    private final TaskExecutionService taskExecutionService;
    private ConcurrentMap<Integer, SlotContext> contexts;

    public DefaultSlotService(NodeEngineImpl nodeEngine, TaskExecutionService taskExecutionService, boolean dynamicSlot, int slotNumber) {
        this.nodeEngine = nodeEngine;
        this.dynamicSlot = dynamicSlot;
        this.taskExecutionService = taskExecutionService;
        this.slotNumber = slotNumber;
        this.idGenerator = new IdGenerator();
    }

    @Override
    public void init() {
        initStatus = true;
        contexts = new ConcurrentHashMap<>();
        assignedSlots = new ConcurrentHashMap<>();
        unassignedSlots = new ConcurrentHashMap<>();
        unassignedResource = new AtomicReference<>(new ResourceProfile());
        assignedResource = new AtomicReference<>(new ResourceProfile());
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r,
            String.format("hz.%s.seaTunnel.slotService.thread", nodeEngine.getHazelcastInstance().getName())));
        if (!dynamicSlot) {
            initFixedSlots();
        }
        unassignedResource.set(getNodeResource());
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                RetryUtils.retryWithException(() -> {
                    LOGGER.fine("start send heartbeat to resource manager, this address: " + nodeEngine.getClusterService().getThisAddress());
                    sendToMaster(new WorkerHeartbeatOperation(toWorkerProfile())).join();
                    return null;
                }, new RetryUtils.RetryMaterial(HEARTBEAT_RETRY_TIME, true, e -> true, DEFAULT_HEARTBEAT_TIMEOUT));
            } catch (Exception e) {
                LOGGER.severe(e);
                LOGGER.severe("failed send heartbeat to resource manager, will retry later. this address: " + nodeEngine.getClusterService().getThisAddress());
            }
        }, 0, DEFAULT_HEARTBEAT_TIMEOUT, TimeUnit.MILLISECONDS);
    }

    @Override
    public void reset() {
        if (!initStatus) {
            synchronized (this) {
                if (!initStatus) {
                    this.close();
                    init();
                }
            }
        }
    }

    @Override
    public synchronized SlotAndWorkerProfile requestSlot(long jobId, ResourceProfile resourceProfile) {
        initStatus = false;
        LOGGER.info(String.format("received slot request, jobID: %d, resource profile: %s", jobId, resourceProfile));
        SlotProfile profile = selectBestMatchSlot(resourceProfile);
        if (profile != null) {
            profile.assign(jobId);
            assignedResource.accumulateAndGet(profile.getResourceProfile(), ResourceProfile::merge);
            unassignedResource.accumulateAndGet(profile.getResourceProfile(), ResourceProfile::subtract);
            unassignedSlots.remove(profile.getSlotID());
            assignedSlots.put(profile.getSlotID(), profile);
            contexts.computeIfAbsent(profile.getSlotID(), p -> new SlotContext(profile.getSlotID(), taskExecutionService));
        }
        return new SlotAndWorkerProfile(toWorkerProfile(), profile);
    }

    public SlotContext getSlotContext(SlotProfile slotProfile) {
        if (!contexts.containsKey(slotProfile.getSlotID())) {
            throw new WrongTargetSlotException("Unknown slot in slot service, slot profile: " + slotProfile);
        }
        return contexts.get(slotProfile.getSlotID());
    }

    @Override
    public void releaseSlot(long jobId, SlotProfile profile) {
        LOGGER.info(String.format("received slot release request, jobID: %d, slot: %s", jobId, profile));
        if (!assignedSlots.containsKey(profile.getSlotID())) {
            throw new WrongTargetSlotException("Not exist this slot in slot service, slot profile: " + profile);
        }

        if (assignedSlots.get(profile.getSlotID()).getOwnerJobID() != jobId) {
            throw new WrongTargetSlotException(String.format("The profile %s not belong with job %d",
                    assignedSlots.get(profile.getSlotID()), jobId));
        }

        assignedResource.accumulateAndGet(profile.getResourceProfile(), ResourceProfile::subtract);
        unassignedResource.accumulateAndGet(profile.getResourceProfile(), ResourceProfile::merge);
        profile.unassigned();
        if (!dynamicSlot) {
            unassignedSlots.put(profile.getSlotID(), profile);
        }
        assignedSlots.remove(profile.getSlotID());
        contexts.remove(profile.getSlotID());
    }

    @Override
    public void close() {
        scheduledExecutorService.shutdown();
    }

    private SlotProfile selectBestMatchSlot(ResourceProfile profile) {
        if (unassignedSlots.isEmpty() && !dynamicSlot) {
            return null;
        }
        if (dynamicSlot) {
            if (unassignedResource.get().enoughThan(profile)) {
                return new SlotProfile(nodeEngine.getThisAddress(), (int) idGenerator.getNextId(), profile);
            }
        } else {
            Optional<SlotProfile> result = unassignedSlots.values().stream()
                    .filter(slot -> slot.getResourceProfile().enoughThan(profile))
                    .min((slot1, slot2) -> {
                        if (slot1.getResourceProfile().getHeapMemory().getBytes() != slot2.getResourceProfile().getHeapMemory().getBytes()) {
                            return slot1.getResourceProfile().getHeapMemory().getBytes() - slot2.getResourceProfile().getHeapMemory().getBytes() >= 0 ? 1 : -1;
                        } else {
                            return slot1.getResourceProfile().getCpu().getCore() - slot2.getResourceProfile().getCpu().getCore();
                        }
                    });
            return result.orElse(null);
        }
        return null;
    }

    private void initFixedSlots() {
        long maxMemory = Runtime.getRuntime().maxMemory();
        for (int i = 0; i < slotNumber; i++) {
            unassignedSlots.put(i, new SlotProfile(nodeEngine.getThisAddress(), i,
                    new ResourceProfile(CPU.of(0), Memory.of(maxMemory / slotNumber))));
        }
    }

    public WorkerProfile toWorkerProfile() {
        WorkerProfile workerProfile = new WorkerProfile(nodeEngine.getThisAddress());
        workerProfile.setProfile(getNodeResource());
        workerProfile.setAssignedSlots(assignedSlots.values().toArray(new SlotProfile[0]));
        workerProfile.setUnassignedSlots(unassignedSlots.values().toArray(new SlotProfile[0]));
        workerProfile.setUnassignedResource(unassignedResource.get());
        return workerProfile;
    }

    private ResourceProfile getNodeResource() {
        return new ResourceProfile(CPU.of(0), Memory.of(Runtime.getRuntime().maxMemory()));
    }

    public <E> InvocationFuture<E> sendToMaster(Operation operation) {
        InvocationBuilder invocationBuilder = nodeEngine.getOperationService().createInvocationBuilder(SeaTunnelServer.SERVICE_NAME, operation, nodeEngine.getMasterAddress());
        return invocationBuilder.invoke();
    }

}

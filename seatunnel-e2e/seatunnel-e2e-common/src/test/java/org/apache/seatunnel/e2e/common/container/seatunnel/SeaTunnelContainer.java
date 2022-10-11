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

package org.apache.seatunnel.e2e.common.container.seatunnel;

import org.apache.seatunnel.e2e.common.container.AbstractTestContainer;
import org.apache.seatunnel.e2e.common.container.ContainerExtendedFactory;

import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

@NoArgsConstructor
//@AutoService(TestContainer.class)
// TODO add AutoService after engine feature is ready
public class SeaTunnelContainer extends AbstractTestContainer {

    private static final Logger LOG = LoggerFactory.getLogger(SeaTunnelContainer.class);
    private static final String JDK_DOCKER_IMAGE = "openjdk:8";
    protected static final Network NETWORK = Network.newNetwork();

    private static final String SEATUNNEL_HOME = "/tmp/seatunnel";
    private static final String SEATUNNEL_BIN = Paths.get(SEATUNNEL_HOME, "bin").toString();
    private static final String CLIENT_SHELL = "seatunnel.sh";
    private static final String SERVER_SHELL = "seatunnel-cluster.sh";
    private GenericContainer<?> server;

    @Override
    public void startUp() throws Exception {
        server = new GenericContainer<>(getDockerImage())
            .withNetwork(NETWORK)
            .withCommand(Paths.get(SEATUNNEL_BIN, SERVER_SHELL).toString())
            .withNetworkAliases("server")
            .withExposedPorts()
            .withLogConsumer(new Slf4jLogConsumer(LOG))
            .waitingFor(Wait.forLogMessage(".*received new worker register.*\\n", 1));
        server.start();
        copySeaTunnelStarter(server);
        // execute extra commands
        // TODO copy config file
        executeExtraCommands(server);
    }

    @Override
    public void tearDown() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Override
    protected String getDockerImage() {
        return JDK_DOCKER_IMAGE;
    }

    @Override
    protected String getStartModuleName() {
        return "seatunnel-starter";
    }

    @Override
    protected String getStartShellName() {
        return CLIENT_SHELL;
    }

    @Override
    protected String getConnectorModulePath() {
        return "seatunnel-connectors-v2";
    }

    @Override
    protected String getConnectorType() {
        return "seatunnel";
    }

    @Override
    protected String getConnectorNamePrefix() {
        return "connector-";
    }

    @Override
    protected List<String> getExtraStartShellCommands() {
        return Collections.emptyList();
    }

    @Override
    public String identifier() {
        return "SeaTunnel";
    }

    @Override
    public void executeExtraCommands(ContainerExtendedFactory extendedFactory) throws IOException, InterruptedException {
        extendedFactory.extend(server);
    }

    @Override
    public Container.ExecResult executeJob(String confFile) throws IOException, InterruptedException {
        return executeJob(server, confFile);
    }
}

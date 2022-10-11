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

package org.apache.seatunnel.e2e.flink.v2.file;

import org.apache.seatunnel.e2e.flink.FlinkContainer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;

import java.io.IOException;

public class FakeSourceToFileIT extends FlinkContainer {

    /**
     * fake source -> local text file sink
     */
    @Test
    public void testFakeSourceToLocalFileText() throws IOException, InterruptedException {
        Container.ExecResult execResult = executeSeaTunnelFlinkJob("/file/fakesource_to_local_text.conf");
        Assertions.assertEquals(0, execResult.getExitCode());
    }

    /**
     *  fake source -> local parquet file sink
     */
    @Test
    public void testFakeSourceToLocalFileParquet() throws IOException, InterruptedException {
        Container.ExecResult execResult = executeSeaTunnelFlinkJob("/file/fakesource_to_local_parquet.conf");
        Assertions.assertEquals(0, execResult.getExitCode());
    }

    /**
     *  fake source -> local json file sink
     */
    @Test
    public void testFakeSourceToLocalFileJson() throws IOException, InterruptedException {
        Container.ExecResult execResult = executeSeaTunnelFlinkJob("/file/fakesource_to_local_json.conf");
        Assertions.assertEquals(0, execResult.getExitCode());
    }
}

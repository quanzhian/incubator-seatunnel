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

package org.apache.seatunnel.admin.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ProcessCleanStream extends Thread {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessCleanStream.class);

    private static final int THREAD_SLEEP_NUM = 50;
    private static final int LOGS_LINE_NUM = 10000;
    private static final int LOGS_LENGTH_NUM = 256 * 1024;
    private static final String DEF_CHAR_SET = "utf-8";

    InputStream inputStream;
    String processName;
    String type;
    StringBuilder logs = new StringBuilder();
    private Boolean isEnd = false;

    public String getLogs() throws InterruptedException {
        int total = 0;
        while (!isEnd) {
            Thread.sleep(THREAD_SLEEP_NUM);
            total += THREAD_SLEEP_NUM;
            if (total > LOGS_LINE_NUM) {
                break;
            }
        }
        return logs.toString();
    }

    public ProcessCleanStream(InputStream stream, String processName, String type) {
        this.inputStream = stream;
        this.processName = processName;
        this.type = type;
    }

    @Override
    public void run() {
        InputStreamReader isr = null;
        try {
            isr = new InputStreamReader(inputStream, DEF_CHAR_SET);
            BufferedReader br = new BufferedReader(isr);
            String line;
            while ((line = br.readLine()) != null) {
                if (logs.length() < LOGS_LENGTH_NUM) {
                    logs.append(line).append("\n");
                } else {
                    logs.append(line, 0, LOGS_LENGTH_NUM).append("\n");
                }
                if (type.equalsIgnoreCase("error")) {
                    LOGGER.warn("[" + processName + "]" + line);
                } else if (type.equalsIgnoreCase("info")) {
                    LOGGER.info("[" + processName + "]" + line);
                }
            }
        } catch (IOException ioe) {
            LOGGER.error("log stream exception", ioe);
        } finally {
            isEnd = true;
            if (isr != null) {
                try {
                    isr.close();
                } catch (IOException ignored) {
                    LOGGER.error("ignored", ignored);
                }
            }
        }
    }
}

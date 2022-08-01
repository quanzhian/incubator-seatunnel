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

package org.apache.seatunnel.connectors.seatunnel.file.sink.writer;

import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.common.utils.VariablesSubstitute;
import org.apache.seatunnel.connectors.seatunnel.file.config.Constant;

import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class FileSinkPartitionDirNameGenerator implements PartitionDirNameGenerator {
    private List<String> partitionFieldList;

    private List<Integer> partitionFieldsIndexInRow;

    private String partitionDirExpression;

    private String[] keys;

    private String[] values;

    public FileSinkPartitionDirNameGenerator(List<String> partitionFieldList,
                                             List<Integer> partitionFieldsIndexInRow,
                                             String partitionDirExpression) {
        this.partitionFieldList = partitionFieldList;
        this.partitionFieldsIndexInRow = partitionFieldsIndexInRow;
        this.partitionDirExpression = partitionDirExpression;

        if (!CollectionUtils.isEmpty(partitionFieldList)) {
            keys = new String[partitionFieldList.size()];
            values = new String[partitionFieldList.size()];
            for (int i = 0; i < partitionFieldList.size(); i++) {
                keys[i] = "k" + i;
                values[i] = "v" + i;
            }
        }
    }

    @Override
    public Map<String, List<String>> generatorPartitionDir(SeaTunnelRow seaTunnelRow) {
        Map<String, List<String>> partitionDirAndValsMap = new HashMap<>(1);
        if (CollectionUtils.isEmpty(this.partitionFieldsIndexInRow)) {
            partitionDirAndValsMap.put(Constant.NON_PARTITION, null);
            return partitionDirAndValsMap;
        }

        List<String> vals = new ArrayList<>(partitionFieldsIndexInRow.size());
        String partitionDir;
        if (StringUtils.isBlank(partitionDirExpression)) {
            StringBuilder sbd = new StringBuilder();
            for (int i = 0; i < partitionFieldsIndexInRow.size(); i++) {
                sbd.append(partitionFieldList.get(i))
                    .append("=")
                    .append(seaTunnelRow.getFields()[partitionFieldsIndexInRow.get(i)])
                    .append("/");
                vals.add(seaTunnelRow.getFields()[partitionFieldsIndexInRow.get(i)].toString());
            }
            partitionDir = sbd.toString();
        } else {
            Map<String, String> valueMap = new HashMap<>(partitionFieldList.size() * 2);
            for (int i = 0; i < partitionFieldsIndexInRow.size(); i++) {
                valueMap.put(keys[i], partitionFieldList.get(i));
                valueMap.put(values[i], seaTunnelRow.getFields()[partitionFieldsIndexInRow.get(i)].toString());
                vals.add(seaTunnelRow.getFields()[partitionFieldsIndexInRow.get(i)].toString());
            }
            partitionDir = VariablesSubstitute.substitute(partitionDirExpression, valueMap);
        }

        partitionDirAndValsMap.put(partitionDir, vals);
        return partitionDirAndValsMap;
    }
}

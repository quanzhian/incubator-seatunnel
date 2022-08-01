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

package org.apache.seatunnel.translation.spark.common.serialization;

import org.apache.seatunnel.api.table.type.MapType;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.translation.serialization.RowConverter;
import org.apache.seatunnel.translation.spark.common.utils.TypeConverterUtils;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.MutableAny;
import org.apache.spark.sql.catalyst.expressions.MutableBoolean;
import org.apache.spark.sql.catalyst.expressions.MutableByte;
import org.apache.spark.sql.catalyst.expressions.MutableDouble;
import org.apache.spark.sql.catalyst.expressions.MutableFloat;
import org.apache.spark.sql.catalyst.expressions.MutableInt;
import org.apache.spark.sql.catalyst.expressions.MutableLong;
import org.apache.spark.sql.catalyst.expressions.MutableShort;
import org.apache.spark.sql.catalyst.expressions.MutableValue;
import org.apache.spark.sql.catalyst.expressions.SpecificInternalRow;
import org.apache.spark.unsafe.types.UTF8String;

import java.io.IOException;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public final class InternalRowConverter extends RowConverter<InternalRow> {

    public InternalRowConverter(SeaTunnelDataType<?> dataType) {
        super(dataType);
    }

    @Override
    public InternalRow convert(SeaTunnelRow seaTunnelRow) throws IOException {
        validate(seaTunnelRow);
        return (InternalRow) convert(seaTunnelRow, dataType);
    }

    private static Object convert(Object field, SeaTunnelDataType<?> dataType) {
        if (field == null) {
            return null;
        }
        switch (dataType.getSqlType()) {
            case ROW:
                SeaTunnelRow seaTunnelRow = (SeaTunnelRow) field;
                SeaTunnelRowType rowType = (SeaTunnelRowType) dataType;
                return convert(seaTunnelRow, rowType);
            case DATE:
                return Date.valueOf((LocalDate) field);
            case TIME:
                //TODO: how reconvert?
                LocalTime localTime = (LocalTime) field;
                return Timestamp.valueOf(LocalDateTime.of(LocalDate.ofEpochDay(0), localTime));
            case TIMESTAMP:
                return Timestamp.valueOf((LocalDateTime) field);
            case MAP:
                return convertMap((Map<?, ?>) field, (MapType<?, ?>) dataType, InternalRowConverter::convert);
            case STRING:
                return UTF8String.fromString((String) field);
            default:
                return field;
        }
    }

    private static InternalRow convert(SeaTunnelRow seaTunnelRow, SeaTunnelRowType rowType) {
        int arity = rowType.getTotalFields();
        MutableValue[] values = new MutableValue[arity];
        for (int i = 0; i < arity; i++) {
            values[i] = createMutableValue(rowType.getFieldType(i));
            if (TypeConverterUtils.ROW_KIND_FIELD.equals(rowType.getFieldName(i))) {
                values[i].update(seaTunnelRow.getRowKind().toByteValue());
            } else {
                values[i].update(convert(seaTunnelRow.getField(i), rowType.getFieldType(i)));
            }
        }
        return new SpecificInternalRow(values);
    }

    private static Object convertMap(Map<?, ?> mapData, MapType<?, ?> mapType, BiFunction<Object, SeaTunnelDataType<?>, Object> convertFunction) {
        if (mapData == null || mapData.size() == 0) {
            return mapData;
        }
        switch (mapType.getValueType().getSqlType()) {
            case MAP:
            case ROW:
            case DATE:
            case TIME:
            case TIMESTAMP:
                Map<Object, Object> newMap = new HashMap<>(mapData.size());
                mapData.forEach((key, value) -> {
                    SeaTunnelDataType<?> valueType = mapType.getValueType();
                    newMap.put(key, convertFunction.apply(value, valueType));
                });
                return newMap;
            default:
                return mapData;
        }
    }

    private static MutableValue createMutableValue(SeaTunnelDataType<?> dataType) {
        switch (dataType.getSqlType()) {
            case BOOLEAN:
                return new MutableBoolean();
            case TINYINT:
                return new MutableByte();
            case SMALLINT:
                return new MutableShort();
            case INT:
                return new MutableInt();
            case BIGINT:
                return new MutableLong();
            case FLOAT:
                return new MutableFloat();
            case DOUBLE:
                return new MutableDouble();
            default:
                return new MutableAny();
        }
    }

    @Override
    public SeaTunnelRow convert(InternalRow engineRow) throws IOException {
        return (SeaTunnelRow) reconvert(engineRow, dataType);
    }

    public static Object reconvert(Object field, SeaTunnelDataType<?> dataType) {
        if (field == null) {
            return null;
        }

        switch (dataType.getSqlType()) {
            case ROW:
                return reconvert((InternalRow) field, (SeaTunnelRowType) dataType);
            case DATE:
                return ((Date) field).toLocalDate();
            case TIME:
                return ((Timestamp) field).toLocalDateTime().toLocalTime();
            case TIMESTAMP:
                return ((Timestamp) field).toLocalDateTime();
            case MAP:
                return convertMap((Map<?, ?>) field, (MapType<?, ?>) dataType, InternalRowConverter::reconvert);
            case STRING:
                return field.toString();
            default:
                return field;
        }
    }

    public static SeaTunnelRow reconvert(InternalRow engineRow, SeaTunnelRowType rowType) {
        Object[] fields = new Object[engineRow.numFields()];
        for (int i = 0; i < engineRow.numFields(); i++) {
            fields[i] = reconvert(engineRow.get(i, TypeConverterUtils.convert(rowType.getFieldType(i))),
                rowType.getFieldType(i));
        }
        return new SeaTunnelRow(fields);
    }
}

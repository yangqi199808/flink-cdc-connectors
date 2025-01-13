/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.cdc.connectors.sqlserver.source.utils;

import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.types.DataType;

import io.debezium.relational.Column;

import java.sql.Types;

/** Utilities for converting from SqlServer types to Flink types. */
public class SqlServerTypeUtils {

    /** Microsoft SQL type GUID's type name. */
    static final String UNIQUEIDENTIFIRER = "uniqueidentifier";

    /** Returns a corresponding Flink data type from a debezium {@link Column}. */
    public static DataType fromDbzColumn(Column column) {
        DataType dataType = convertFromColumn(column);
        if (column.isOptional()) {
            return dataType;
        } else {
            return dataType.notNull();
        }
    }

    /**
     * Returns a corresponding Flink data type from a debezium {@link Column} with nullable always
     * be true.
     */
    private static DataType convertFromColumn(Column column) {
        switch (column.jdbcType()) {
            case Types.CHAR:
            case Types.VARCHAR:
            case Types.NCHAR:
            case Types.NVARCHAR:
            case Types.STRUCT:
            case Types.CLOB:
                return DataTypes.STRING();
            case Types.BLOB:
                return DataTypes.BYTES();
            case Types.INTEGER:
            case Types.SMALLINT:
            case Types.TINYINT:
                return DataTypes.INT();
            case Types.BIGINT:
                return DataTypes.BIGINT();
            case Types.FLOAT:
            case Types.REAL:
            case Types.DOUBLE:
            case Types.NUMERIC:
            case Types.DECIMAL:
                return DataTypes.DECIMAL(column.length(), column.scale().orElse(0));
            case Types.DATE:
                return DataTypes.DATE();
            case Types.TIMESTAMP:
            case Types.TIMESTAMP_WITH_TIMEZONE:
                return column.scale().isPresent()
                        ? DataTypes.TIMESTAMP(column.scale().get())
                        : DataTypes.TIMESTAMP();
            case Types.BOOLEAN:
                return DataTypes.BOOLEAN();
            default:
                throw new UnsupportedOperationException(
                        String.format(
                                "Don't support SqlSever type '%s' yet, jdbcType:'%s'.",
                                column.typeName(), column.jdbcType()));
        }
    }
}

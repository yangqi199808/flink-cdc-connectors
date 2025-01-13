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

package org.apache.flink.cdc.connectors.base.dialect;

import org.apache.flink.cdc.common.annotation.Experimental;
import org.apache.flink.cdc.connectors.base.config.JdbcSourceConfig;
import org.apache.flink.cdc.connectors.base.config.SourceConfig;
import org.apache.flink.cdc.connectors.base.relational.connection.JdbcConnectionPoolFactory;
import org.apache.flink.cdc.connectors.base.relational.connection.JdbcConnectionPools;
import org.apache.flink.cdc.connectors.base.source.meta.split.SourceSplitBase;
import org.apache.flink.cdc.connectors.base.source.reader.external.FetchTask;

import io.debezium.jdbc.JdbcConnection;
import io.debezium.relational.TableId;
import io.debezium.relational.history.TableChanges.TableChange;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** The dialect of JDBC data source. */
@Experimental
public interface JdbcDataSourceDialect extends DataSourceDialect<JdbcSourceConfig> {

    /** Discovers the list of table to capture. */
    @Override
    List<TableId> discoverDataCollections(JdbcSourceConfig sourceConfig);

    /** Discovers the captured tables' schema by {@link SourceConfig}. */
    @Override
    Map<TableId, TableChange> discoverDataCollectionSchemas(JdbcSourceConfig sourceConfig);

    /**
     * Creates and opens a new {@link JdbcConnection} backing connection pool.
     *
     * @param sourceConfig a basic source configuration.
     * @return a utility that simplifies using a JDBC connection.
     */
    JdbcConnection openJdbcConnection(JdbcSourceConfig sourceConfig);

    /** Get a connection pool factory to create connection pool. */
    JdbcConnectionPoolFactory getPooledDataSourceFactory();

    /** Query and build the schema of table. */
    TableChange queryTableSchema(JdbcConnection jdbc, TableId tableId);

    @Override
    FetchTask<SourceSplitBase> createFetchTask(SourceSplitBase sourceSplitBase);

    default void close() throws IOException {
        JdbcConnectionPools.getInstance(getPooledDataSourceFactory()).clear();
    }
}

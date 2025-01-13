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

import org.apache.flink.cdc.connectors.base.utils.ObjectUtils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Unit test for {@link SqlServerUtils.SQLServerUUIDComparator}. * */
public class SQLServerUUIDComparatorTest {

    @Test
    public void testComparator() {
        SqlServerUtils.SQLServerUUIDComparator comparator =
                new SqlServerUtils.SQLServerUUIDComparator();
        // Create an ArrayList and fill it with Guid values.
        List<UUID> guidList = new ArrayList<>();
        guidList.add(UUID.fromString("3AAAAAAA-BBBB-CCCC-DDDD-2EEEEEEEEEEE"));
        guidList.add(UUID.fromString("2AAAAAAA-BBBB-CCCC-DDDD-1EEEEEEEEEEE"));
        guidList.add(UUID.fromString("1AAAAAAA-BBBB-CCCC-DDDD-3EEEEEEEEEEE"));

        // Sort the Guids.
        guidList.sort(ObjectUtils::compare);

        assertEquals(
                guidList.get(0).toString().toUpperCase(), "1AAAAAAA-BBBB-CCCC-DDDD-3EEEEEEEEEEE");
        assertEquals(
                guidList.get(1).toString().toUpperCase(), "2AAAAAAA-BBBB-CCCC-DDDD-1EEEEEEEEEEE");
        assertEquals(
                guidList.get(2).toString().toUpperCase(), "3AAAAAAA-BBBB-CCCC-DDDD-2EEEEEEEEEEE");

        // Create an ArrayList of SqlGuids.
        List<UUID> sqlGuidList = new ArrayList<>();
        sqlGuidList.add(UUID.fromString("3AAAAAAA-BBBB-CCCC-DDDD-2EEEEEEEEEEE"));
        sqlGuidList.add(UUID.fromString("2AAAAAAA-BBBB-CCCC-DDDD-1EEEEEEEEEEE"));
        sqlGuidList.add(UUID.fromString("1AAAAAAA-BBBB-CCCC-DDDD-3EEEEEEEEEEE"));

        // Sort the SqlGuids. The unsorted SqlGuids are in the same order
        // as the unsorted Guid values.
        sqlGuidList.sort(comparator);

        // Display the sorted SqlGuids. The sorted SqlGuid values are ordered
        // differently than the Guid values.
        assertEquals(
                sqlGuidList.get(0).toString().toUpperCase(),
                "2AAAAAAA-BBBB-CCCC-DDDD-1EEEEEEEEEEE");
        assertEquals(
                sqlGuidList.get(1).toString().toUpperCase(),
                "3AAAAAAA-BBBB-CCCC-DDDD-2EEEEEEEEEEE");
        assertEquals(
                sqlGuidList.get(2).toString().toUpperCase(),
                "1AAAAAAA-BBBB-CCCC-DDDD-3EEEEEEEEEEE");
    }
}

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

package org.apache.flink.cdc.pipeline.tests;

import org.apache.flink.api.common.JobStatus;
import org.apache.flink.api.common.time.Deadline;
import org.apache.flink.cdc.common.test.utils.TestUtils;
import org.apache.flink.cdc.connectors.maxcompute.options.MaxComputeOptions;
import org.apache.flink.cdc.connectors.maxcompute.utils.MaxComputeUtils;
import org.apache.flink.cdc.pipeline.tests.utils.PipelineTestEnvironment;
import org.apache.flink.client.program.rest.RestClusterClient;
import org.apache.flink.runtime.client.JobStatusMessage;
import org.apache.flink.table.api.ValidationException;

import com.aliyun.odps.Instance;
import com.aliyun.odps.Odps;
import com.aliyun.odps.data.Record;
import com.aliyun.odps.task.SQLTask;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** End-to-end tests for maxcompute cdc pipeline job. */
public class MaxComputeE2eITCase extends PipelineTestEnvironment {
    private static final Logger LOG = LoggerFactory.getLogger(MaxComputeE2eITCase.class);

    public static final DockerImageName MAXCOMPUTE_IMAGE =
            DockerImageName.parse("maxcompute/maxcompute-emulator:v0.0.7");

    @ClassRule
    public static GenericContainer<?> maxcompute =
            new GenericContainer<>(MAXCOMPUTE_IMAGE)
                    .withExposedPorts(8080)
                    .waitingFor(
                            Wait.forLogMessage(".*Started MaxcomputeEmulatorApplication.*\\n", 1))
                    .withLogConsumer(frame -> System.out.print(frame.getUtf8String()));

    public final MaxComputeOptions testOptions =
            MaxComputeOptions.builder("ak", "sk", getEndpoint(), "mocked_mc")
                    .withTunnelEndpoint(getEndpoint())
                    .build();

    @Test
    public void testSingleSplitSingleTable() throws Exception {
        startTest("SINGLE_SPLIT_SINGLE_TABLE");
        Instance instance =
                SQLTask.run(
                        MaxComputeUtils.getOdps(testOptions),
                        "select * from table1 order by col1;");
        instance.waitForSuccess();
        List<Record> result = SQLTask.getResult(instance);
        System.out.println(result);
        Assert.assertEquals(2, result.size());
        // 2,x
        Assert.assertEquals("2", result.get(0).get(0));
        Assert.assertEquals("x", result.get(0).get(1));
        // 3, NULL (MaxCompute Emulator use 'NULL' instead of null)
        Assert.assertEquals("3", result.get(1).get(0));
        Assert.assertEquals("NULL", result.get(1).get(1));
    }

    private void startTest(String testSet) throws Exception {
        sendPOST(getEndpoint() + "/init", getEndpoint());

        Odps odps = MaxComputeUtils.getOdps(testOptions);
        odps.tables().delete("table1", true);
        odps.tables().delete("table2", true);

        String pipelineJob =
                "source:\n"
                        + "   type: values\n"
                        + "   name: ValuesSource\n"
                        + "   event-set.id: "
                        + testSet
                        + "\n"
                        + "\n"
                        + "sink:\n"
                        + "   type: maxcompute\n"
                        + "   name: MaxComputeSink\n"
                        + "   accessId: ak\n"
                        + "   accessKey: sk\n"
                        + "   endpoint: "
                        + getEndpoint()
                        + "\n"
                        + "   tunnelEndpoint: "
                        + getEndpoint()
                        + "\n"
                        + "   project: mocked_mc\n"
                        + "   bucketsNum: 8\n"
                        + "   compressAlgorithm: raw\n"
                        + "\n"
                        + "pipeline:\n"
                        + "   parallelism: 4";
        Path maxcomputeCdcJar = TestUtils.getResource("maxcompute-cdc-pipeline-connector.jar");
        Path valuesCdcJar = TestUtils.getResource("values-cdc-pipeline-connector.jar");
        submitPipelineJob(pipelineJob, maxcomputeCdcJar, valuesCdcJar);
        waitUntilJobFinished(Duration.ofMinutes(10));
        LOG.info("Pipeline job is running");
    }

    private String getEndpoint() {
        String ip;
        if (maxcompute.getHost().equals("localhost")) {
            try {
                ip = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException e) {
                ip = "127.0.0.1";
            }
        } else {
            ip = maxcompute.getHost();
        }
        return "http://" + ip + ":" + maxcompute.getFirstMappedPort();
    }

    public static void sendPOST(String postUrl, String postData) throws Exception {
        URL url = new URL(postUrl);

        HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
        httpURLConnection.setRequestMethod("POST");
        httpURLConnection.setDoOutput(true);
        httpURLConnection.setRequestProperty("Content-Type", "application/json");
        httpURLConnection.setRequestProperty("Content-Length", String.valueOf(postData.length()));

        try (OutputStream outputStream = httpURLConnection.getOutputStream()) {
            outputStream.write(postData.getBytes("UTF-8"));
            outputStream.flush();
        }
        int responseCode = httpURLConnection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("POST request failed with response code: " + responseCode);
        }
    }

    public void waitUntilJobFinished(Duration timeout) {
        RestClusterClient<?> clusterClient = getRestClusterClient();
        Deadline deadline = Deadline.fromNow(timeout);
        while (deadline.hasTimeLeft()) {
            Collection<JobStatusMessage> jobStatusMessages;
            try {
                jobStatusMessages = clusterClient.listJobs().get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                LOG.warn("Error when fetching job status.", e);
                continue;
            }
            if (jobStatusMessages != null && !jobStatusMessages.isEmpty()) {
                JobStatusMessage message = jobStatusMessages.iterator().next();
                JobStatus jobStatus = message.getJobState();
                if (jobStatus.isTerminalState()) {
                    if (jobStatus == JobStatus.FINISHED) {
                        return;
                    }
                    throw new ValidationException(
                            String.format(
                                    "Job has been terminated unexpectedly! JobName: %s, JobID: %s, Status: %s",
                                    message.getJobName(),
                                    message.getJobId(),
                                    message.getJobState()));
                }
            }
        }
    }
}

/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2018 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.test.integration.domain.suspendresume;

import static org.jboss.as.controller.client.helpers.ClientConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.domain.DeploymentPlan;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.client.helpers.domain.DomainDeploymentManager;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.common.HttpRequest;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.domain.suites.DomainTestSuite;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.test.suspendresumeendpoint.SuspendResumeHandler;
import org.wildfly.test.suspendresumeendpoint.TestSuspendServiceActivator;
import org.wildfly.test.suspendresumeendpoint.TestUndertowService;

/**
 * Tests suspend-servers and resume-servers operation under host level.
 *
 * @author Yeray Borges
 */
public class HostSuspendResumeTestCase {
    public static final String WEB_SUSPEND_JAR = "web-suspend.jar";
    public static final String MAIN_SERVER_GROUP = "main-server-group";

    public static final PathAddress SLAVE_ADDR = PathAddress.pathAddress(HOST, "slave");
    public static final PathAddress MASTER_ADDR = PathAddress.pathAddress(HOST, "master");

    public static final PathAddress SERVER_MAIN_ONE = PathAddress.pathAddress(SERVER, "main-one");
    public static final PathAddress SERVER_MAIN_THREE = PathAddress.pathAddress(SERVER, "main-three");
    public static final String SUSPEND_STATE = "suspend-state";

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainMasterLifecycleUtil;
    private static DomainLifecycleUtil domainSlaveLifecycleUtil;

    @BeforeClass
    public static void setupDomain() {
        testSupport = DomainTestSuite.createSupport(HostSuspendResumeTestCase.class.getSimpleName());

        domainMasterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
        domainSlaveLifecycleUtil = testSupport.getDomainSlaveLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() {
        testSupport = null;
        domainMasterLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    @Before
    public void deployTestApplication() throws Exception {
        DomainClient client = domainMasterLifecycleUtil.getDomainClient();

        DomainDeploymentManager deploymentManager = client.getDeploymentManager();
        DeploymentPlan plan = deploymentManager.newDeploymentPlan()
                .add(WEB_SUSPEND_JAR, createDeployment().as(ZipExporter.class).exportAsInputStream())
                .andDeploy().toServerGroup(MAIN_SERVER_GROUP)
                .build();

        deploymentManager.execute(plan).get();
    }

    @After
    public void undeployTestApplication() throws Exception {
        DomainClient client = domainMasterLifecycleUtil.getDomainClient();

        DomainDeploymentManager deploymentManager = client.getDeploymentManager();
        DeploymentPlan plan = deploymentManager.newDeploymentPlan().undeploy(WEB_SUSPEND_JAR)
                .andRemoveUndeployed()
                .toServerGroup(MAIN_SERVER_GROUP)
                .build();

        deploymentManager.execute(plan).get();
    }

    @Test
    public void hostSuspendAndResume() throws Exception {
        final String appUrl = "http://" + TestSuiteEnvironment.getServerAddress() + ":8080/web-suspend";

        final ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {

            Future<Object> result = executorService.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    return HttpRequest.get(appUrl, 60, TimeUnit.SECONDS);
                }
            });

            TimeUnit.SECONDS.sleep(1); //nasty, but we need to make sure the HTTP request has started

            final DomainClient masterClient = domainMasterLifecycleUtil.getDomainClient();
            final DomainClient slaveClient = domainSlaveLifecycleUtil.getDomainClient();

            //suspend servers in master, it is a blocking operation, so we use a different thread and master client
            executorService.submit(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    ModelNode op = Util.createOperation("suspend-servers", MASTER_ADDR);
                    op.get(ModelDescriptionConstants.SUSPEND_TIMEOUT).set(TimeoutUtil.adjust(30));
                    return DomainTestUtils.executeForResult(op, domainMasterLifecycleUtil.createDomainClient()).asString();
                }
            });

            //Wait and check the server in master is suspending
            DomainTestUtils.waitUntilSuspendState(masterClient, MASTER_ADDR.append(SERVER_MAIN_ONE), "SUSPENDING");

            //servers in slave must not be unaffected
            ModelNode op = Util.getReadAttributeOperation(SLAVE_ADDR.append(SERVER_MAIN_THREE), SUSPEND_STATE);
            Assert.assertEquals("RUNNING", DomainTestUtils.executeForResult(op, slaveClient).asString());

            //unlock the web application, so the server could get suspended state
            HttpRequest.get(appUrl + "?" + TestUndertowService.SKIP_GRACEFUL + "=true", TimeoutUtil.adjust(30), TimeUnit.SECONDS);
            Assert.assertEquals(SuspendResumeHandler.TEXT, result.get());

            op = Util.getReadAttributeOperation(MASTER_ADDR.append(SERVER_MAIN_ONE), SUSPEND_STATE);
            Assert.assertEquals("SUSPENDED", DomainTestUtils.executeForResult(op, masterClient).asString());

            //resume servers in master
            op = Util.createOperation("resume-servers", MASTER_ADDR);
            DomainTestUtils.executeForResult(op, masterClient);

            //suspend state should running
            op = Util.getReadAttributeOperation(MASTER_ADDR.append(SERVER_MAIN_ONE), SUSPEND_STATE);
            Assert.assertEquals("RUNNING", DomainTestUtils.executeForResult(op, masterClient).asString());

            op = Util.getReadAttributeOperation(SLAVE_ADDR.append(SERVER_MAIN_THREE), SUSPEND_STATE);
            Assert.assertEquals("RUNNING", DomainTestUtils.executeForResult(op, slaveClient).asString());

        } finally {
            HttpRequest.get(appUrl + "?" + TestUndertowService.SKIP_GRACEFUL, 10, TimeUnit.SECONDS);
            executorService.shutdown();
        }
    }

    private static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class, WEB_SUSPEND_JAR)
                .addPackage(SuspendResumeHandler.class.getPackage())
                .addAsServiceProvider(ServiceActivator.class, TestSuspendServiceActivator.class)
                .addAsResource(new StringAsset("Dependencies: org.jboss.dmr, org.jboss.as.controller, io.undertow.core, org.jboss.as.server,org.wildfly.extension.request-controller, org.jboss.as.network\n"),
                        "META-INF/MANIFEST.MF");
    }
}

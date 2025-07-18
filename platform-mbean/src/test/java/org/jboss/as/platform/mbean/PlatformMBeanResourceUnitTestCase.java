/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILDREN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MODEL_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NILLABLE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PLATFORM_MBEAN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_RESOURCES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_TYPES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;

import java.io.IOException;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.PlatformLoggingMXBean;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit tests of platform mbean integration into the management layer.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class PlatformMBeanResourceUnitTestCase {

    private static ServiceContainer container;
    private static ModelController controller;
    private static ModelControllerClient client;

    @BeforeClass
    public static void setupController() throws InterruptedException {
        container = ServiceContainer.Factory.create("test");
        ServiceTarget target = container.subTarget();
        PlatformMBeanTestModelControllerService svc = new PlatformMBeanTestModelControllerService();
        ServiceBuilder<ModelController> builder = target.addService(ServiceName.of("ModelController"), svc);
        builder.install();
        svc.latch.await(30, TimeUnit.SECONDS);
        controller = svc.getValue();
        ModelNode setup = Util.getEmptyOperation("setup", new ModelNode());
        controller.execute(setup, null, null, null);

        client = svc.getModelControllerClientFactory().createClient(Executors.newSingleThreadExecutor());
    }

    @AfterClass
    public static void shutdownServiceContainer() {
        if (container != null) {
            container.shutdown();
            try {
                container.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            finally {
                container = null;
            }
        }
        if (client != null) {
            try {
                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void testRootResource() throws IOException {
        ModelNode op = getOperation(READ_RESOURCE_DESCRIPTION_OPERATION, null, null);
        op.get(RECURSIVE).set(true);
        op.get(OPERATIONS).set(true);

        ModelNode result = executeOp(op, false);
        Assert.assertTrue(result.isDefined());

        int baseTypeCount = PlatformMBeanConstants.JDK_NOCOMPILATION_TYPES.size();
        if (ManagementFactory.getCompilationMXBean() != null) {
            baseTypeCount++;
        }
        baseTypeCount += 1;
        final ModelNode childTypes = result.get(CHILDREN, TYPE, MODEL_DESCRIPTION);
        Assert.assertEquals(baseTypeCount, childTypes.asPropertyList().size());

        op = getOperation(READ_CHILDREN_NAMES_OPERATION, null, null);
        op.get(CHILD_TYPE).set(PlatformMBeanConstants.TYPE);

        result = executeOp(op, false);
        Assert.assertTrue(result.isDefined());
        Assert.assertEquals(baseTypeCount, result.asList().size());

        op = getOperation(READ_CHILDREN_RESOURCES_OPERATION, null, null);
        op.get(CHILD_TYPE).set(PlatformMBeanConstants.TYPE);

        result = executeOp(op, false);
        Assert.assertTrue(result.isDefined());
        Assert.assertEquals(baseTypeCount, result.asPropertyList().size());

        op = getOperation(READ_CHILDREN_TYPES_OPERATION, null, null);

        result = executeOp(op, false);
        Assert.assertTrue(result.isDefined());
        Assert.assertEquals(1, result.asList().size());
        Assert.assertEquals(TYPE, result.get(0).asString());

        op = getOperation(READ_RESOURCE_OPERATION, null, null);
        op.get(INCLUDE_RUNTIME).set(true);

        result = executeOp(op, false);
        Assert.assertTrue(result.isDefined());
    }

    @Test
    public void testClassLoadingMXBean() throws IOException {
        DescribedResource describedResource = basicResourceTest("class-loading", null);

        ClassLoadingMXBean mbean = ManagementFactory.getClassLoadingMXBean();
        boolean verbose = describedResource.resource.get("verbose").asBoolean();
        Assert.assertEquals(mbean.isVerbose(), verbose);

        ModelNode op = getOperation("write-attribute", "class-loading", null);
        op.get("name").set("verbose");
        op.get("value").set(!verbose);
        executeOp(op, false);
        Assert.assertEquals(mbean.isVerbose(), !verbose);

        // Restore
        mbean.setVerbose(verbose);
    }

    @Test
    public void testCompilationMXBean() throws IOException {
        if (ManagementFactory.getCompilationMXBean() == null) {
            ModelNode op = getOperation(READ_RESOURCE_DESCRIPTION_OPERATION, "compilation", null);
            executeOp(op, true);
            op = getOperation(READ_RESOURCE_OPERATION, "compilation", null);
            executeOp(op, true);

            return;
        }

        DescribedResource describedResource = basicResourceTest("compilation", null);
        // TODO validate values
    }

    @Test
    public void testGarbageCollectorMXBean() throws IOException {
        List<ModelNode> gcs = rootWithNamedChildResourceTest("garbage-collector");

        for (ModelNode node : gcs) {
            DescribedResource describedResource = basicResourceTest("garbage-collector", node.asString());
            // TODO validate values
        }
    }

    @Test
    public void testMemoryMXBean() throws IOException {
        DescribedResource describedResource = basicResourceTest("memory", null);

        MemoryMXBean mbean = ManagementFactory.getMemoryMXBean();
        boolean verbose = describedResource.resource.get("verbose").asBoolean();
        Assert.assertEquals(mbean.isVerbose(), verbose);

        ModelNode op = getOperation("write-attribute", "memory", null);
        op.get("name").set("verbose");
        op.get("value").set(!verbose);
        executeOp(op, false);
        Assert.assertEquals(mbean.isVerbose(), !verbose);

        // Restore
        mbean.setVerbose(verbose);

        op = getOperation("gc", "memory", null);
        Assert.assertFalse(executeOp(op, false).isDefined());
    }

    @Test
    public void testMemoryManagerMXBean() throws IOException {
        List<ModelNode> mms = rootWithNamedChildResourceTest("memory-manager");

        for (ModelNode node : mms) {
            DescribedResource describedResource = basicResourceTest("memory-manager", node.asString());
            // TODO validate values
        }
    }

    @Test
    public void testMemoryPoolMXBean() throws IOException {
        List<ModelNode> mps = rootWithNamedChildResourceTest("memory-pool");

        Map<String, MemoryPoolMXBean> mbeans = new HashMap<String, MemoryPoolMXBean>();
        for (MemoryPoolMXBean mbean : ManagementFactory.getMemoryPoolMXBeans()) {
            mbeans.put(PlatformMBeanUtil.escapeMBeanName(mbean.getName()), mbean);
        }

        for (ModelNode node : mps) {
            DescribedResource describedResource = basicResourceTest("memory-pool", node.asString());

            MemoryPoolMXBean mbean = mbeans.get(describedResource.resource.get("name").asString());
            Assert.assertNotNull(mbean);

            boolean usageSupported = describedResource.resource.get("usage-threshold-supported").asBoolean();
            Assert.assertEquals(mbean.isUsageThresholdSupported(), usageSupported);
            boolean collUsageSupported = describedResource.resource.get("collection-usage-threshold-supported").asBoolean();
            Assert.assertEquals(mbean.isCollectionUsageThresholdSupported(), collUsageSupported);

            ModelNode op = getOperation("reset-peak-usage", "memory-pool", node.asString());
            Assert.assertFalse(executeOp(op, false).isDefined());

            op = getOperation("write-attribute", "memory-pool", node.asString());
            op.get("name").set("usage-threshold");
            op.get("value").set(1024 * 100);
            ModelNode result = executeOp(op, !usageSupported);
            if (usageSupported) {
                Assert.assertEquals(1024 * 100, mbean.getUsageThreshold());
            }

            op = getOperation("write-attribute", "memory-pool", node.asString());
            op.get("name").set("collection-usage-threshold");
            op.get("value").set(1024 * 100);
            result = executeOp(op, !collUsageSupported);
            if (collUsageSupported) {
                Assert.assertEquals(1024 * 100, mbean.getCollectionUsageThreshold());
            }
        }
    }

    @Test
    public void testOperatingSystemMXBean() throws IOException {
        DescribedResource describedResource = basicResourceTest("operating-system", null);
        // TODO validate values
    }

    // Per AS7-2185 we do not support interacting with the PlatformLoggingMXBean via
    // the main management API
//    @Ignore("[AS7-2185]")
//    @Test
//    public void testPlatformLoggingMXBean() throws IOException {
//        if (PlatformMBeanUtil.JVM_MAJOR_VERSION < 7) {
//            ModelNode op = getOperation(READ_RESOURCE_DESCRIPTION_OPERATION, "logging", null);
//            executeOp(op, true);
//            op = getOperation(READ_RESOURCE_OPERATION, "logging", null);
//            executeOp(op, true);
//
//            return;
//        }
//
//        DescribedResource describedResource = basicResourceTest("logging", null);
//        // TODO validate values
//
//        java.util.logging.Logger.getLogger("test.platform.logging");
//        java.util.logging.Logger.getLogger("test.platform.logging.mbean").setLevel(Level.FINE);
//
//        ModelNode op = getOperation("set-logger-level", "logging", null);
//        op.get("logger-name").set("test.platform.logging.mbean");
//        op.get("level-name").set(Level.SEVERE.getName());
//        Assert.assertFalse(executeOp(op, false).isDefined());
//
//        op = getOperation("get-logger-level", "logging", null);
//        op.get("logger-name").set("test.platform.logging.mbean");
//        ModelNode result = executeOp(op, false);
//        Assert.assertTrue(result.isDefined());
//        Assert.assertEquals(Level.SEVERE.getName(), result.asString());
//
//        op = getOperation("get-parent-logger-name", "logging", null);
//        op.get("logger-name").set("test.platform.logging.mbean");
//        result = executeOp(op, false);
//        Assert.assertTrue(result.isDefined());
//        Assert.assertEquals("test.platform.logging", result.asString());
//    }

    @Test
    public void testRuntimeMXBean() throws IOException {
        DescribedResource describedResource = basicResourceTest("runtime", null);
        // TODO validate values
    }

    @Test
    public void testThreadingMXBean() throws IOException {
        DescribedResource describedResource = basicResourceTest("threading", null);

        ThreadMXBean mbean = ManagementFactory.getThreadMXBean();
        boolean syncSupported = describedResource.resource.get(PlatformMBeanConstants.SYNCHRONIZER_USAGE_SUPPORTED).asBoolean();
        Assert.assertEquals(mbean.isSynchronizerUsageSupported(), syncSupported);
        boolean monitorSupported = describedResource.resource.get(PlatformMBeanConstants.OBJECT_MONITOR_USAGE_SUPPORTED).asBoolean();
        Assert.assertEquals(mbean.isObjectMonitorUsageSupported(), monitorSupported);
        boolean threadContentionSupported = describedResource.resource.get(PlatformMBeanConstants.THREAD_CONTENTION_MONITORING_SUPPORTED).asBoolean();
        Assert.assertEquals(mbean.isThreadContentionMonitoringSupported(), threadContentionSupported);
        boolean threadContentionEnabled = describedResource.resource.get(PlatformMBeanConstants.THREAD_CONTENTION_MONITORING_ENABLED).asBoolean();
        Assert.assertEquals(mbean.isThreadContentionMonitoringEnabled(), threadContentionEnabled);
        boolean threadCPUSupported = describedResource.resource.get(PlatformMBeanConstants.THREAD_CPU_TIME_SUPPORTED).asBoolean();
        Assert.assertEquals(mbean.isThreadCpuTimeSupported(), threadCPUSupported);
        boolean currentThreadPUSupported = describedResource.resource.get(PlatformMBeanConstants.CURRENT_THREAD_CPU_TIME_SUPPORTED).asBoolean();
        Assert.assertEquals(mbean.isCurrentThreadCpuTimeSupported(), currentThreadPUSupported);
        boolean threadCPUEnabled = false;
        if (threadCPUSupported || currentThreadPUSupported) {
            threadCPUEnabled = describedResource.resource.get(PlatformMBeanConstants.THREAD_CPU_TIME_ENABLED).asBoolean();
            Assert.assertEquals(mbean.isThreadCpuTimeSupported(), threadCPUEnabled);
        }

        ModelNode op = getOperation("reset-peak-thread-count", "threading", null);
        Assert.assertFalse(executeOp(op, false).isDefined());

        op = getOperation("find-deadlocked-threads", "threading", null);
        ModelNode result = executeOp(op, !syncSupported);
        if (syncSupported && result.isDefined()) {
            Assert.assertEquals(ModelType.LIST, result.getType());
        }
        op = getOperation("find-monitor-deadlocked-threads", "threading", null);
        result = executeOp(op, !monitorSupported);
        if (monitorSupported && result.isDefined()) {
            Assert.assertEquals(ModelType.LIST, result.getType());
        }

        op = getOperation("dump-all-threads", "threading", null);
        op.get("locked-monitors").set(false);
        op.get("locked-synchronizers").set(false);
        result = executeOp(op, false);
        Assert.assertEquals(ModelType.LIST, result.getType());
        long mainThreadId = findMainThread(result);

        op = getOperation("dump-all-threads", "threading", null);
        op.get("locked-monitors").set(true);
        op.get("locked-synchronizers").set(false);
        result = executeOp(op, !monitorSupported);
        if (monitorSupported) {
            Assert.assertEquals(ModelType.LIST, result.getType());
        }

        op = getOperation("dump-all-threads", "threading", null);
        op.get("locked-monitors").set(false);
        op.get("locked-synchronizers").set(true);
        result = executeOp(op, !syncSupported);
        if (syncSupported) {
            Assert.assertEquals(ModelType.LIST, result.getType());
        }

        op = getOperation("dump-all-threads", "threading", null);
        op.get("locked-monitors").set(true);
        op.get("locked-synchronizers").set(true);
        boolean canDump = syncSupported && monitorSupported;
        result = executeOp(op, !canDump);
        if (canDump) {
            Assert.assertEquals(ModelType.LIST, result.getType());
        }

        op = getOperation("get-thread-info", "threading", null);
        op.get("id").set(mainThreadId);
        result = executeOp(op, false);
        Assert.assertTrue(result.isDefined());
        Assert.assertEquals("main", result.get("thread-name").asString());
        Assert.assertEquals(mainThreadId, result.get("thread-id").asLong());
        List<ModelNode> list = result.get("stack-trace").asList();
        Assert.assertEquals(0, list.size());

        op = getOperation("get-thread-info", "threading", null);
        op.get("id").set(mainThreadId);
        op.get("max-depth").set(2);
        result = executeOp(op, false);
        Assert.assertTrue(result.isDefined());
        Assert.assertEquals("main", result.get("thread-name").asString());
        Assert.assertEquals(mainThreadId, result.get("thread-id").asLong());
        list = result.get("stack-trace").asList();
        Assert.assertEquals(2, list.size());

        op = getOperation("get-thread-infos", "threading", null);
        op.get("ids").add(mainThreadId);
        result = executeOp(op, false);
        Assert.assertEquals(ModelType.LIST, result.getType());
        List<ModelNode> threads = result.asList();
        Assert.assertEquals(1, threads.size());
        ModelNode thread = threads.get(0);
        Assert.assertEquals("main", thread.get("thread-name").asString());
        Assert.assertEquals(mainThreadId, thread.get("thread-id").asLong());
        list = thread.get("stack-trace").asList();
        Assert.assertEquals(0, list.size());

        op = getOperation("get-thread-infos", "threading", null);
        op.get("ids").add(mainThreadId);
        op.get("max-depth").set(2);
        result = executeOp(op, false);
        Assert.assertEquals(ModelType.LIST, result.getType());
        threads = result.asList();
        Assert.assertEquals(1, threads.size());
        thread = threads.get(0);
        Assert.assertEquals("main", thread.get("thread-name").asString());
        Assert.assertEquals(mainThreadId, thread.get("thread-id").asLong());
        list = thread.get("stack-trace").asList();
        Assert.assertEquals(2, list.size());

        op = getOperation("get-thread-infos", "threading", null);
        op.get("ids").add(mainThreadId);
        op.get("locked-monitors").set(true);
        op.get("locked-synchronizers").set(true);
        result = executeOp(op, !canDump);
        if (canDump) {
            Assert.assertEquals(ModelType.LIST, result.getType());
            threads = result.asList();
            Assert.assertEquals(1, threads.size());
            thread = threads.get(0);
            Assert.assertEquals("main", thread.get("thread-name").asString());
            Assert.assertEquals(mainThreadId, thread.get("thread-id").asLong());
            list = thread.get("stack-trace").asList();
            Assert.assertTrue(list.size() > 1);
        }
        if(threadCPUSupported) {
            op = getOperation("get-thread-cpu-time", "threading", null);
            op.get("id").set(mainThreadId);
            result = executeOp(op, !threadCPUSupported);
            Assert.assertEquals(ModelType.LONG, result.getType());
            if (!threadCPUEnabled) {
                Assert.assertEquals(-1L, result.asLong());
            }

            op = getOperation("get-thread-user-time", "threading", null);
            op.get("id").set(mainThreadId);
            result = executeOp(op, !threadCPUSupported);
            Assert.assertEquals(ModelType.LONG, result.getType());
            if (!threadCPUEnabled) {
                Assert.assertEquals(-1L, result.asLong());
            }
        }

        if (threadCPUSupported || currentThreadPUSupported) {
            op = getOperation("write-attribute", "threading", null);
            op.get("name").set("thread-cpu-time-enabled");
            op.get("value").set(!threadCPUEnabled);
            executeOp(op, false);
            Assert.assertEquals(mbean.isThreadCpuTimeEnabled(), !threadCPUEnabled);
            mbean.setThreadCpuTimeEnabled(threadCPUEnabled); // restore
        }
        op = getOperation("write-attribute", "threading", null);
        op.get("name").set("thread-contention-monitoring-enabled");
        op.get("value").set(!threadContentionEnabled);
        executeOp(op, false);
        Assert.assertEquals(mbean.isThreadContentionMonitoringEnabled(), !threadContentionEnabled);
        mbean.setThreadContentionMonitoringEnabled(threadContentionEnabled); // restore

        if (mbean instanceof com.sun.management.ThreadMXBean) {
            com.sun.management.ThreadMXBean extMbean = (com.sun.management.ThreadMXBean) mbean;
            boolean allocatedMemorySupported = describedResource.resource.get(PlatformMBeanConstants.THREAD_ALLOCATED_MEMORY_SUPPORTED).asBoolean();
            Assert.assertEquals(extMbean.isThreadAllocatedMemorySupported(), allocatedMemorySupported);
            if (allocatedMemorySupported) {
                boolean allocatedMemoryEnabled = describedResource.resource.get(PlatformMBeanConstants.THREAD_ALLOCATED_MEMORY_ENABLED).asBoolean();
                Assert.assertEquals(extMbean.isThreadAllocatedMemoryEnabled(), allocatedMemoryEnabled);

                op = getOperation("write-attribute", "threading", null);
                op.get("name").set("thread-allocated-memory-enabled");
                op.get("value").set(!allocatedMemoryEnabled);
                executeOp(op, false);
                Assert.assertEquals(extMbean.isThreadAllocatedMemoryEnabled(), !allocatedMemoryEnabled);
                extMbean.setThreadAllocatedMemoryEnabled(allocatedMemoryEnabled); // restore

                op = getOperation("get-thread-allocated-bytes", "threading", null);
                op.get("id").set(mainThreadId);
                result = executeOp(op, !allocatedMemorySupported);
                Assert.assertEquals(ModelType.LONG, result.getType());
                if (!allocatedMemoryEnabled) {
                    Assert.assertEquals(-1L, result.asLong());
                }

                op = getOperation("get-threads-allocated-bytes", "threading", null);
                op.get("ids").add(mainThreadId);
                result = executeOp(op, !allocatedMemorySupported);
                Assert.assertEquals(ModelType.LIST, result.getType());
                List<ModelNode> allocatedBytes = result.asList();
                Assert.assertEquals(1, allocatedBytes.size());
                ModelNode allocatedByte = allocatedBytes.get(0);
                Assert.assertEquals(ModelType.LONG, allocatedByte.getType());
                if (!allocatedMemoryEnabled) {
                    Assert.assertEquals(-1L, allocatedByte.asLong());
                }
            }
            if (threadCPUSupported) {
                op = getOperation("get-thread-cpu-times", "threading", null);
                op.get("ids").add(mainThreadId);
                result = executeOp(op, !threadCPUSupported);
                Assert.assertEquals(ModelType.LIST, result.getType());
                List<ModelNode> cpuTimes = result.asList();
                Assert.assertEquals(1, cpuTimes.size());
                ModelNode cpuTime = cpuTimes.get(0);
                Assert.assertEquals(ModelType.LONG, cpuTime.getType());
                if (!threadCPUEnabled) {
                    Assert.assertEquals(-1L, cpuTime.asLong());
                }

                op = getOperation("get-thread-user-times", "threading", null);
                op.get("ids").add(mainThreadId);
                result = executeOp(op, !threadCPUSupported);
                Assert.assertEquals(ModelType.LIST, result.getType());
                List<ModelNode> userTimes = result.asList();
                Assert.assertEquals(1, userTimes.size());
                ModelNode userTime = userTimes.get(0);
                Assert.assertEquals(ModelType.LONG, userTime.getType());
                if (!threadCPUEnabled) {
                    Assert.assertEquals(-1L, userTime.asLong());
                }
            }
        }

    }

    @Test
    public void testPlatformLoggingMXBean() throws IOException {
        DescribedResource describedResource = basicResourceTest("platform-logging", null);
        List<ModelNode> loggers = describedResource.resource.get(PlatformMBeanConstants.LOGGER_NAMES).asList();
        List<String> apiLoggers = ManagementFactory.getPlatformMXBean(PlatformLoggingMXBean.class).getLoggerNames();
        Assert.assertEquals(apiLoggers.size(), loggers.size());
    }

    private long findMainThread(ModelNode result) {
        List<ModelNode> threads = result.asList();
        for (ModelNode thread : threads) {
            ModelNode name = thread.get("thread-name");
            Assert.assertEquals(ModelType.STRING, name.getType());
            if ("main".equals(name.asString())) {
                return thread.get("thread-id").asLong();
            }
        }
        throw new IllegalStateException("No thread 'main' found");
    }

    private List<ModelNode> rootWithNamedChildResourceTest(final String childType) throws IOException {
        ModelNode op = getOperation(READ_RESOURCE_DESCRIPTION_OPERATION, childType, null);
        op.get(RECURSIVE).set(true);
        op.get(OPERATIONS).set(true);

        ModelNode result = executeOp(op, false);
        Assert.assertTrue(result.isDefined());
        final ModelNode childTypes = result.get(CHILDREN, TYPE, MODEL_DESCRIPTION);

        op = getOperation(READ_CHILDREN_NAMES_OPERATION, childType, null);
        op.get(CHILD_TYPE).set(ModelDescriptionConstants.NAME);

        result = executeOp(op, false);
        Assert.assertTrue(result.isDefined());
        List<ModelNode> children = result.asList();
        int childCount =  children.size();

        op = getOperation(READ_CHILDREN_RESOURCES_OPERATION, childType, null);
        op.get(CHILD_TYPE).set(ModelDescriptionConstants.NAME);

        result = executeOp(op, false);
        Assert.assertTrue(result.isDefined());
        Assert.assertEquals(childCount, result.asPropertyList().size());

        op = getOperation(READ_CHILDREN_TYPES_OPERATION, childType, null);

        result = executeOp(op, false);
        Assert.assertTrue(result.isDefined());
        Assert.assertEquals(1, result.asList().size());
        Assert.assertEquals(ModelDescriptionConstants.NAME, result.get(0).asString());

        op = getOperation(READ_RESOURCE_OPERATION, childType, null);
        op.get(INCLUDE_RUNTIME).set(true);

        result = executeOp(op, false);
        Assert.assertTrue(result.isDefined());

        return children;
    }

    private DescribedResource basicResourceTest(final String type, final String name) throws IOException {

        ModelNode op = getOperation(READ_RESOURCE_DESCRIPTION_OPERATION, type, name);
        op.get(RECURSIVE).set(true);
        op.get(OPERATIONS).set(true);

        ModelNode result = executeOp(op, false);
        Assert.assertTrue(result.isDefined());
        final ModelNode description = result;

        op = getOperation(READ_RESOURCE_OPERATION, type, name);
        op.get(INCLUDE_RUNTIME).set(true);

        result = executeOp(op, false);
        Assert.assertTrue(result.isDefined());
        final ModelNode resource = result;
        validateResource(description, resource);

        for (Property prop : resource.asPropertyList()) {
            op = getOperation(READ_ATTRIBUTE_OPERATION, type, name);
            op.get(NAME).set(prop.getName());
            final ModelNode response = client.execute(op);
            if (SUCCESS.equals(response.get(OUTCOME).asString())) {
                final ModelNode resultNode = response.get(RESULT);
                if (resultNode.isDefined())  {
                    Assert.assertEquals(prop.getName() + " has correct ModelType", description.get(ATTRIBUTES, prop.getName(), TYPE).asType(), resultNode.getType());
                } else {
                    Assert.assertTrue(prop.getName() + " is undefined", description.get(ATTRIBUTES, prop.getName(), NILLABLE).asBoolean());
                }
            } else {
                Assert.assertFalse(prop.getValue().isDefined());
            }
        }

        return new DescribedResource(description, resource);
    }

    private void validateResource(ModelNode description, ModelNode resource) {
        final List<Property> attrDescs = description.get(ATTRIBUTES).asPropertyList();
        final List<Property> attributes = resource.asPropertyList();
        Assert.assertEquals("Correct number of attributes", attrDescs.size(), attributes.size());
        for (Property prop : attributes) {
            final ModelNode desc = description.get(ATTRIBUTES, prop.getName());
            Assert.assertTrue(desc.isDefined());
            final ModelNode attrVal = prop.getValue();
            if (attrVal.isDefined()) {
                Assert.assertEquals(prop.getName() + " has incorrect ModelType", desc.get(TYPE).asType(), attrVal.getType());
            } else {
                /*
                    In case of three special properties no assert possible on some platforms
                 */
                if(!prop.getName().equals("thread-cpu-time-enabled")
                        && !prop.getName().equals("current-thread-cpu-time")
                        && !prop.getName().equals("current-thread-user-time"))
                    Assert.assertTrue(prop.getName() + " is undefined", desc.get(NILLABLE).asBoolean());
            }
        }
    }

    private ModelNode executeOp(ModelNode op, boolean expectFailure) throws IOException {
        ModelNode response = client.execute(op);
        if (expectFailure) {
            Assert.assertEquals(FAILED, response.get(OUTCOME).asString());
            return response.get(FAILURE_DESCRIPTION);
        } else {
            Assert.assertEquals(response.get(FAILURE_DESCRIPTION).asString(),SUCCESS, response.get(OUTCOME).asString());
            return response.get(RESULT);
        }
    }

    private static ModelNode getOperation(final String opName, final String type, final String name) {
        return Util.getEmptyOperation(opName,  getAddress(type, name));
    }

    private static ModelNode getAddress(final String type, final String name) {
        final ModelNode result = new ModelNode();
        result.add(CORE_SERVICE, PLATFORM_MBEAN);
        if (type != null) {
            result.add(TYPE, type);
            if (name != null) {
                result.add(NAME, name);
            }
        }

        return result;
    }

    private static class DescribedResource {
        final ModelNode description;
        final ModelNode resource;


        public DescribedResource(ModelNode description, ModelNode resource) {
            this.description = description;
            this.resource = resource;
        }
    }
}

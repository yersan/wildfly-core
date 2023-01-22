package org.wildfly.core.instmgr;

import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.platform.mbean.PlatformMBeanConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INSTALLATION_MANAGER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

public class InstMgrResourceUnitTest {

    private static ServiceContainer container;
    private static ModelController controller;
    private static ModelControllerClient client;

    @BeforeClass
    public static void setupController() throws InterruptedException {
        container = ServiceContainer.Factory.create("test");
        ServiceTarget target = container.subTarget();

        InstMgrModelControllerService svc = new InstMgrModelControllerService();
        ServiceBuilder<ModelController> builder = target.addService(ServiceName.of("ModelController"), svc);
        builder.install();
        svc.latch.await(30, TimeUnit.SECONDS);
        controller = svc.getValue();
        ModelNode setup = Util.getEmptyOperation("setup", new ModelNode());
        controller.execute(setup, null, null, null);

        client = controller.createClient(Executors.newSingleThreadExecutor());
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
    public void testRootResource() throws Exception {
        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(READ_RESOURCE_DESCRIPTION_OPERATION, pathElements);
        op.get(RECURSIVE).set(true);
        op.get(OPERATIONS).set(true);

        ModelNode result = executeForResult(op);



        Assert.assertTrue(true);
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


    public ModelNode executeForResult(final ModelNode operation) throws Exception {
        try {
            final ModelNode result = client.execute(operation);
            if (!ClientConstants.SUCCESS.equals(result.get(ClientConstants.OUTCOME).asString())) {
                throw new Exception(result.get(ClientConstants.FAILURE_DESCRIPTION).toString());
            }
            return result.get(ClientConstants.RESULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

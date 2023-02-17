package org.wildfly.core.instmgr;

import org.jboss.as.controller.AbstractControllerService;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.controller.services.path.PathResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StabilityMonitor;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.core.instmgr.spi.ProsperoInstallationManager;
import org.wildfly.core.instmgr.spi.ProsperoInstallationManagerFactory;
import org.wildfly.installationmanager.Channel;
import org.wildfly.installationmanager.InstallationManagerFinder;
import org.wildfly.installationmanager.Repository;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;

/**
 * Installation Manager unit tests.
 */
public class InstMgrResourceTestCase extends AbstractControllerTestBase {
    private static final ServiceName PATH_MANAGER_SVC = AbstractControllerService.PATH_MANAGER_CAPABILITY.getCapabilityServiceName();
    PathManagerService pathManagerService;

    static final Path JBOSS_HOME = Paths.get(System.getProperty("basedir", ".")).resolve("target").resolve("InstMgrResourceTestCase").normalize().toAbsolutePath();
    static final Path JBOSS_CONTROLLER_TEMP_DIR = JBOSS_HOME.resolve("temp");
    static final Path INSTALLATION_MANAGER_PROPERTIES = JBOSS_HOME.resolve("bin").resolve("installation-manager.properties");

    @Before
    @Override
    public void setupController() throws InterruptedException, IOException {
        ProsperoInstallationManager.initData();
        JBOSS_HOME.resolve("bin").toFile().mkdirs();
        Files.createFile(INSTALLATION_MANAGER_PROPERTIES);
        try (FileOutputStream out = new FileOutputStream(INSTALLATION_MANAGER_PROPERTIES.toString())) {
            final Properties prop = new Properties();
            prop.setProperty(InstMgrCandidateStatus.INST_MGR_STATUS_KEY, InstMgrCandidateStatus.Status.CLEAN.name());
            prop.store(out, null);
        }
        super.setupController();
    }

    @After
    @Override
    public void shutdownServiceContainer() throws IOException {
        super.shutdownServiceContainer();
        if (JBOSS_HOME.toFile().exists()) {
            Files.walk(JBOSS_HOME)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Override
    protected void initModel(ManagementModel managementModel) {
        ManagementResourceRegistration registration = managementModel.getRootResourceRegistration();
        pathManagerService = new PathManagerService(managementModel.getCapabilityRegistry()) {
            {
                super.addHardcodedAbsolutePath(getContainer(), "jboss.home.dir", JBOSS_HOME.toString());
                super.addHardcodedAbsolutePath(getContainer(), "jboss.controller.temp.dir", JBOSS_CONTROLLER_TEMP_DIR.toString());
            }
        };
        GlobalOperationHandlers.registerGlobalOperations(registration, processType);

        GlobalNotifications.registerGlobalNotifications(registration, processType);

        StabilityMonitor monitor = new StabilityMonitor();
        monitor.addController(getContainer().addService(PATH_MANAGER_SVC).setInstance(pathManagerService).install());

        try {
            monitor.awaitStability(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        registration.registerSubModel(PathResourceDefinition.createSpecified(pathManagerService));

        pathManagerService.addPathManagerResources(managementModel.getRootResource());
    }

    @Test
    public void testRootResource() throws Exception {
        ModelNode op = Util.createEmptyOperation(READ_RESOURCE_DESCRIPTION_OPERATION, null);
        op.get(RECURSIVE).set(true);
        op.get(OPERATIONS).set(true);

        ModelNode result = executeForResult(op);
        Assert.assertTrue(result.isDefined());

        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        op = Util.createEmptyOperation(READ_RESOURCE_DESCRIPTION_OPERATION, pathElements);
        op.get(RECURSIVE).set(true);

        result = executeForResult(op);
        Assert.assertTrue(result.isDefined());
    }

    @Test
    public void testCanReadChannels() throws Exception {
        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(READ_RESOURCE_OPERATION, pathElements);
        op.get(INCLUDE_RUNTIME).set(true);

        ModelNode result = executeForResult(op);
        Assert.assertTrue(result.isDefined());

        // validate the result:
        List<ModelNode> channels = result.get(InstMgrConstants.CHANNELS).asListOrEmpty();
        Assert.assertEquals(3, channels.size());

        // First Channel
        ModelNode channel = channels.get(0);
        Assert.assertEquals("channel-test-0", channel.get(NAME).asString());

        List<ModelNode> repositories = channel.get(InstMgrConstants.REPOSITORIES).asListOrEmpty();
        Assert.assertEquals(2, repositories.size());

        ModelNode repository = repositories.get(0);
        Assert.assertEquals("id0", repository.get(InstMgrConstants.REPOSITORY_ID).asString());
        Assert.assertEquals("http://localhost", repository.get(InstMgrConstants.REPOSITORY_URL).asString());

        repository = repositories.get(1);
        Assert.assertEquals("id2", repository.get(InstMgrConstants.REPOSITORY_ID).asString());
        Assert.assertEquals("file://dummy", repository.get(InstMgrConstants.REPOSITORY_URL).asString());

        ModelNode manifest = channel.get(InstMgrConstants.MANIFEST);
        Assert.assertTrue(manifest.hasDefined(InstMgrConstants.MANIFEST_GAV));
        Assert.assertFalse(manifest.hasDefined(InstMgrConstants.MANIFEST_URL));
        Assert.assertEquals("org.test.groupid:org.test.artifactid:1.0.0.Final", manifest.get(InstMgrConstants.MANIFEST_GAV).asString());


        // Second Channel
        channel = channels.get(1);
        Assert.assertEquals("channel-test-1", channel.get(NAME).asString());

        repositories = channel.get(InstMgrConstants.REPOSITORIES).asListOrEmpty();
        Assert.assertEquals(1, repositories.size());

        repository = repositories.get(0);
        Assert.assertEquals("id1", repository.get(InstMgrConstants.REPOSITORY_ID).asString());
        Assert.assertEquals("file://dummy", repository.get(InstMgrConstants.REPOSITORY_URL).asString());

        manifest = channel.get(InstMgrConstants.MANIFEST);
        Assert.assertFalse(manifest.hasDefined(InstMgrConstants.MANIFEST_GAV));
        Assert.assertTrue(manifest.hasDefined(InstMgrConstants.MANIFEST_URL));
        Assert.assertEquals("file://dummy", manifest.get(InstMgrConstants.MANIFEST_URL).asString());


        // third Channel
        channel = channels.get(2);
        Assert.assertEquals("channel-test-2", channel.get(NAME).asString());

        repositories = channel.get(InstMgrConstants.REPOSITORIES).asListOrEmpty();
        Assert.assertEquals(2, repositories.size());

        Assert.assertFalse(channel.hasDefined(InstMgrConstants.MANIFEST));
    }

    @Test
    public void testAddEditChannels() throws Exception {
        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(READ_RESOURCE_OPERATION, pathElements);
        op.get(INCLUDE_RUNTIME).set(true);

        // Initial
        ModelNode result = executeForResult(op);
        List<ModelNode> currentChannels = result.get(InstMgrConstants.CHANNELS).asListOrEmpty();
        Assert.assertEquals(3, currentChannels.size());

        // Add one Channel
        pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        op = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, pathElements);

        ModelNode channels = new ModelNode().addEmptyList();

        ModelNode channel = new ModelNode();
        channel.get(NAME).set("channel-test-added");

        ModelNode repositories = new ModelNode().addEmptyList();
        ModelNode repository = new ModelNode();
        repository.get(InstMgrConstants.REPOSITORY_ID).set("id0");
        repository.get(InstMgrConstants.REPOSITORY_URL).set("https://localhost.com");
        repositories.add(repository);

        ModelNode manifest = new ModelNode();
        manifest.get(InstMgrConstants.MANIFEST_GAV).set("group:artifact:version");
        channel.get(InstMgrConstants.MANIFEST).set(manifest);

        channel.get(InstMgrConstants.REPOSITORIES).set(repositories);
        channels.add(channel);

        op.get(NAME).set(InstMgrConstants.CHANNELS);
        op.get(VALUE).set(channels);

        executeCheckNoFailure(op);


        // Read again
        pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        op = Util.createEmptyOperation(READ_RESOURCE_OPERATION, pathElements);
        op.get(INCLUDE_RUNTIME).set(true);

        result = executeForResult(op);
        currentChannels = result.get(InstMgrConstants.CHANNELS).asListOrEmpty();
        Assert.assertEquals(4, currentChannels.size());

        boolean found = false;
        for (Channel storedChannel : ProsperoInstallationManager.lstChannels) {
            if (storedChannel.getName().equals("channel-test-added")) {
                List<Repository> storedRepositories = storedChannel.getRepositories();
                for (Repository storedRepo : storedRepositories) {
                    if (storedRepo.getId().equals("id0") && storedRepo.getUrl().equals("https://localhost.com")) {
                        found = storedChannel.getManifestCoordinate().get().equals("group:artifact:version");
                        break;
                    }
                }
            }
        }
        Assert.assertTrue(found);


        // Edit one channel
        pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        op = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, pathElements);

        channels = new ModelNode().addEmptyList();
        channel = new ModelNode();
        channel.get(NAME).set("channel-test-1");

        repositories = new ModelNode().addEmptyList();
        repository = new ModelNode();
        repository.get(InstMgrConstants.REPOSITORY_ID).set("id-modified-0");
        repository.get(InstMgrConstants.REPOSITORY_URL).set("https://modified.com");
        repositories.add(repository);

        manifest = new ModelNode();
        manifest.get(InstMgrConstants.MANIFEST_GAV).set("group-modified:artifact-modified:version-modified");
        channel.get(InstMgrConstants.MANIFEST).set(manifest);

        channel.get(InstMgrConstants.REPOSITORIES).set(repositories);
        channels.add(channel);

        op.get(NAME).set(InstMgrConstants.CHANNELS);
        op.get(VALUE).set(channels);

        executeCheckNoFailure(op);

        found = false;
        for (Channel storedChannel : ProsperoInstallationManager.lstChannels) {
            if (storedChannel.getName().equals("channel-test-1")) {
                List<Repository> storedRepositories = storedChannel.getRepositories();
                for (Repository storedRepo : storedRepositories) {
                    if (storedRepo.getId().equals("id-modified-0") && storedRepo.getUrl().equals("https://modified.com")) {
                        found = storedChannel.getManifestCoordinate().get().equals("group-modified:artifact-modified:version-modified");
                        break;
                    }
                }
            }
        }
        Assert.assertTrue(found);
    }

    @Test
    public void testCreateSnapShot() throws OperationFailedException {
        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(InstMgrCreateSnapshotHandler.OPERATION_NAME, pathElements);
        op.get(PATH).set(JBOSS_HOME.toString());
        ModelNode result = executeForResult(op);
        Assert.assertTrue(result.asString().contains(JBOSS_HOME + "/test.zip"));
    }
}

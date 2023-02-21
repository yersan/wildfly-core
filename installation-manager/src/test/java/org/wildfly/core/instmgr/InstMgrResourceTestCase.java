package org.wildfly.core.instmgr;

import org.jboss.as.controller.AbstractControllerService;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.controller.services.path.PathResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StabilityMonitor;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.core.instmgr.spi.TestInstallationManager;
import org.wildfly.installationmanager.ArtifactChange;
import org.wildfly.installationmanager.Channel;
import org.wildfly.installationmanager.ChannelChange;
import org.wildfly.installationmanager.Repository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
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
        TestInstallationManager.initData();
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
        Files.deleteIfExists(INSTALLATION_MANAGER_PROPERTIES);
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
    public void testReadChannels() throws Exception {
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
        Assert.assertEquals("id1", repository.get(InstMgrConstants.REPOSITORY_ID).asString());
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
        for (Channel storedChannel : TestInstallationManager.lstChannels) {
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
        for (Channel storedChannel : TestInstallationManager.lstChannels) {
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
    public void testRemoveChannels() throws Exception {

    }

    @Test
    public void testHistoryChannels() throws Exception {
        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(InstMgrHistoryHandler.OPERATION_NAME, pathElements);

        ModelNode result = executeForResult(op);
        Assert.assertEquals(4, result.asListOrEmpty().size());
        Assert.assertTrue(result.getType() == ModelType.LIST);
        List<ModelNode> entries = result.asListOrEmpty();
        for (ModelNode entry : entries) {
            Assert.assertTrue(entry.hasDefined(InstMgrConstants.HISTORY_RESULT_HASH));
            Assert.assertTrue(entry.hasDefined(InstMgrConstants.HISTORY_RESULT_TIMESTAMP));
            Assert.assertTrue(entry.hasDefined(InstMgrConstants.HISTORY_RESULT_TYPE));
        }
    }

    @Test
    public void testRevisionDetails() throws Exception {
        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(InstMgrHistoryHandler.OPERATION_NAME, pathElements);
        op.get(InstMgrConstants.REVISION).set("dummy");

        ModelNode result = executeForResult(op);
        Assert.assertTrue(result.getType() == ModelType.OBJECT);

        // Verify Artifact Changes
        List<ModelNode> resultLst = result.get(InstMgrConstants.HISTORY_RESULT_DETAILED_ARTIFACT_CHANGES).asList();
        Assert.assertEquals(3, resultLst.size());

        for (ModelNode change : resultLst) {
            String status = change.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_STATUS).asString();
            if (status.equals(ArtifactChange.Status.UPDATED.name())) {

                Assert.assertEquals("org.test.groupid1:org.test.artifact1.updated", change.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_NAME).asString());
                Assert.assertEquals("1.0.0.Final", change.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_OLD_VERSION).asString());
                Assert.assertEquals("1.0.1.Final", change.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_NEW_VERSION).asString());

            } else if (status.equals(ArtifactChange.Status.REMOVED.name())) {

                Assert.assertEquals("org.test.groupid1:org.test.artifact1.removed", change.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_NAME).asString());
                Assert.assertEquals("1.0.0.Final", change.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_OLD_VERSION).asString());
                Assert.assertFalse(change.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_NEW_VERSION).isDefined());

            } else if (status.equals(ArtifactChange.Status.INSTALLED.name())) {

                Assert.assertEquals("org.test.groupid1:org.test.artifact1.installed", change.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_NAME).asString());
                Assert.assertFalse(change.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_OLD_VERSION).isDefined());
                Assert.assertEquals("1.0.1.Final", change.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_NEW_VERSION).asString());

            } else {
                Assert.fail("Invalid status found");
            }
        }

        // Verify Channel Changes
        resultLst = result.get(InstMgrConstants.HISTORY_RESULT_DETAILED_CHANNEL_CHANGES).asList();
        Assert.assertEquals(3, resultLst.size());
        for (ModelNode change : resultLst) {
            String status = change.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_STATUS).asString();
            if (status.equals(ChannelChange.Status.MODIFIED.name())) {

                Assert.assertEquals("channel-test-0", change.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NAME).asString());
                Assert.assertEquals("org.channelchange.groupid:org.channelchange.artifactid:1.0.0.Final", change.get(InstMgrConstants.MANIFEST, InstMgrConstants.HISTORY_DETAILED_CHANNEL_OLD_MANIFEST).asString());
                Assert.assertEquals("org.channelchange.groupid:org.channelchange.artifactid:1.0.1.Final", change.get(InstMgrConstants.MANIFEST, InstMgrConstants.HISTORY_DETAILED_CHANNEL_NEW_MANIFEST).asString());

                List<ModelNode> repositories = change.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_REPOSITORIES).asList();
                Assert.assertEquals(3, repositories.size());
                for (ModelNode repository : repositories) {
                    String oldRepo = repository.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_OLD_REPOSITORY).asString();
                    if (oldRepo.equals("id=id0::url=http://channelchange.com")) {
                        Assert.assertEquals("id=id0::url=http://channelchange-modified.com", repository.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NEW_REPOSITORY).asString());
                    } else if (oldRepo.equals("id=id1::url=file://channelchange")) {
                        Assert.assertEquals("id=id1-modified::url=file://channelchange", repository.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NEW_REPOSITORY).asString());
                    } else if (oldRepo.equals("id=id1-added::url=file://channelchange-added")) {
                        Assert.assertFalse(repository.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NEW_REPOSITORY).isDefined());
                    }
                }

            } else if (status.equals(ChannelChange.Status.REMOVED.name())) {

                Assert.assertEquals("channel-test-0", change.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NAME).asString());
                Assert.assertEquals("org.channelchange.groupid:org.channelchange.artifactid:1.0.0.Final", change.get(InstMgrConstants.MANIFEST, InstMgrConstants.HISTORY_DETAILED_CHANNEL_OLD_MANIFEST).asString());
                Assert.assertFalse(change.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NEW_MANIFEST).isDefined());

                List<ModelNode> repositories = change.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_REPOSITORIES).asList();
                Assert.assertEquals(2, repositories.size());
                for (ModelNode repository : repositories) {
                    Assert.assertTrue(repository.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_OLD_REPOSITORY).isDefined());
                    Assert.assertFalse(repository.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NEW_REPOSITORY).isDefined());
                }

            } else if (status.equals(ChannelChange.Status.ADDED.name())) {

                Assert.assertEquals("channel-test-0", change.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NAME).asString());
                Assert.assertFalse(change.get(InstMgrConstants.MANIFEST, InstMgrConstants.HISTORY_DETAILED_CHANNEL_OLD_MANIFEST).isDefined());
                Assert.assertEquals("org.channelchange.groupid:org.channelchange.artifactid:1.0.0.Final", change.get(InstMgrConstants.MANIFEST, InstMgrConstants.HISTORY_DETAILED_CHANNEL_NEW_MANIFEST).asString());

                List<ModelNode> repositories = change.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_REPOSITORIES).asList();
                Assert.assertEquals(2, repositories.size());
                for (ModelNode repository : repositories) {
                    Assert.assertFalse(repository.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_OLD_REPOSITORY).isDefined());
                    Assert.assertTrue(repository.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NEW_REPOSITORY).isDefined());
                }

            } else {
                Assert.fail("Invalid status found");
            }
        }


//
//        List<ModelNode> lines = result.asListOrEmpty();
//        StringBuilder printedOut = new StringBuilder();
//        for (ModelNode line : lines) {
//            printedOut.append(line.asString()).append("\n");
//        }
//        Assert.assertEquals(
//                "Artifact Updates:\n" +
//                        "org.test.groupid1:org.test.artifact1                              [] ==> 1.0.1.Final    \n" +
//                        "org.test.groupid1:org.test.artifact1                              1.0.0.Final     ==> []\n" +
//                        "org.test.groupid1:org.test.artifact1                              1.0.0.Final     ==> 1.0.1.Final    \n" +
//                        "\n" +
//                        "\n" +
//                        "Configuration changes:\n" +
//                        "[Added channel] channel-test-0:\n" +
//                        "\tManifest: [] ==> org.channelchange.groupid:org.channelchange.artifactid:1.0.0.Final\n" +
//                        "\tRepositories:\n" +
//                        "\t\t[] ==> id=id0::url=http://channelchange.com\n" +
//                        "\t\t[] ==> id=id1::url=file://channelchange\n" +
//                        "[Removed channel] channel-test-0:\n" +
//                        "\tManifest: org.channelchange.groupid:org.channelchange.artifactid:1.0.0.Final ==> []\n" +
//                        "\tRepositories:\n" +
//                        "\t\tid=id0::url=http://channelchange.com ==> []\n" +
//                        "\t\tid=id1::url=file://channelchange ==> []\n" +
//                        "[Updated channel] channel-test-0:\n" +
//                        "\tManifest: org.channelchange.groupid:org.channelchange.artifactid:1.0.0.Final ==> org.channelchange.groupid:org.channelchange.artifactid:1.0.1.Final\n" +
//                        "\tRepositories:\n" +
//                        "\t\tid=id0::url=http://channelchange.com ==> []\n" +
//                        "\t\tid=id1::url=file://channelchange ==> []\n" +
//                        "\t\t[] ==> id=id0::url=http://channelchange.com\n" +
//                        "\t\t[] ==> id=id1::url=file://channelchange\n",
//                printedOut.toString());
    }

    @Test
    public void testCreateSnapShot() throws Exception {
        PathAddress pathElements = PathAddress.pathAddress(CORE_SERVICE, InstMgrConstants.TOOL_NAME);
        ModelNode op = Util.createEmptyOperation(InstMgrCreateSnapshotHandler.OPERATION_NAME, pathElements);
        op.get(PATH).set(JBOSS_HOME.toString());

        ModelNode result = executeForResult(op);
        Assert.assertTrue(result.asString().contains(JBOSS_HOME + "/generated.zip"));

        op.get(PATH).set(JBOSS_HOME.resolve("customFile.zip").toString());

        result = executeForResult(op);
        Assert.assertTrue(result.asString().contains(JBOSS_HOME.resolve("customFile.zip").toString()));
    }
}

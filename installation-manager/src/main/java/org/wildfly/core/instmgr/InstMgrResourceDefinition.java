package org.wildfly.core.instmgr;

import org.jboss.as.controller.AbstractControllerService;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.ParametersValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.core.instmgr.logging.InstMgrLogger;
import org.wildfly.installationmanager.Channel;
import org.wildfly.installationmanager.InstallationManagerFinder;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.Repository;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;

public class InstMgrResourceDefinition extends SimpleResourceDefinition {

    static final RuntimeCapability<Void> INSTALLATION_MANAGER_CAPABILITY =
            RuntimeCapability.Builder.of("org.wildfly.core.installationmanager", InstMgrResourceDefinition.class)
                    .build();
    private final ReadHandler readHandler;
    private final WriteHandler writeHandler;
    private final InstMgrService imService;
    private final AbstractControllerService controllerService;

    private static final AttributeDefinition REPOSITORY_ID = new SimpleAttributeDefinitionBuilder(InstMgrConstants.ID, ModelType.STRING)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .build();

    private static final AttributeDefinition REPOSITORY_URL = new SimpleAttributeDefinitionBuilder(InstMgrConstants.REPOSITORY_URL, ModelType.STRING)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .build();

    private static final AttributeDefinition REPOSITORY = new ObjectTypeAttributeDefinition.Builder(InstMgrConstants.REPOSITORY, REPOSITORY_ID, REPOSITORY_URL)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .build();

    private static final AttributeDefinition CHANNEL_REPOSITORIES = new ObjectTypeAttributeDefinition.Builder(InstMgrConstants.REPOSITORIES, REPOSITORY)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .setAlternatives()
            .build();

    private static final AttributeDefinition CHANNEL_NAME = new SimpleAttributeDefinitionBuilder(InstMgrConstants.CHANNEL_NAME, ModelType.STRING)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .build();

    private static final AttributeDefinition MANIFEST_GAV = new SimpleAttributeDefinitionBuilder(InstMgrConstants.MANIFEST_GAV, ModelType.STRING)
            .setAlternatives("url")
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .build();

    private static final AttributeDefinition MANIFEST_URL = new SimpleAttributeDefinitionBuilder(InstMgrConstants.MANIFEST_URL, ModelType.STRING)
            .setAlternatives(MANIFEST_GAV.getName())
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .build();

    private static final AttributeDefinition CHANNEL_MANIFEST = ObjectTypeAttributeDefinition.create(InstMgrConstants.MANIFEST, MANIFEST_GAV, MANIFEST_URL)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    private static final AttributeDefinition CHANNEL = ObjectTypeAttributeDefinition.create(InstMgrConstants.CHANNEL, CHANNEL_NAME, CHANNEL_REPOSITORIES, CHANNEL_MANIFEST)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .build();

    private static final AttributeDefinition CHANNELS = ObjectTypeAttributeDefinition.create(InstMgrConstants.CHANNELS, CHANNEL)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    private static final AttributeDefinition[] attributes = {CHANNELS};
    private final InstallationManagerFactory imf;

    public static PathElement getPath(String name) {
        return PathElement.pathElement(CORE_SERVICE, name);
    }

    public InstMgrResourceDefinition(InstallationManagerFactory imf, AbstractControllerService controllerService, InstMgrService imService) {
        super(new Parameters(getPath(InstMgrConstants.TOOL_NAME), InstMgrResolver.RESOLVER)
                .setRuntime()
        );
        this.imf = imf;
        this.controllerService = controllerService;
        this.imService = imService;
        this.readHandler = new ReadHandler(imService);
        this.writeHandler = new WriteHandler(imService);
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);

        InstMgrHistoryHandler hhh = new InstMgrHistoryHandler(imService);
        resourceRegistration.registerOperationHandler(InstMgrHistoryHandler.DEFINITION, hhh);

        InstMgrCreateSnapshotHandler hcsh = new InstMgrCreateSnapshotHandler(imService);
        resourceRegistration.registerOperationHandler(InstMgrCreateSnapshotHandler.DEFINITION, hcsh);

        InstMgrListUpdatesHandler imfuh = new InstMgrListUpdatesHandler(imService, imf);
        resourceRegistration.registerOperationHandler(InstMgrListUpdatesHandler.DEFINITION, imfuh);

        InstMgrCleanHandler imch = new InstMgrCleanHandler(imService, imf);
        resourceRegistration.registerOperationHandler(InstMgrCleanHandler.DEFINITION, imch);

        InstMgrPrepareUpdateHandler impuh = new InstMgrPrepareUpdateHandler(imService, imf);
        resourceRegistration.registerOperationHandler(InstMgrPrepareUpdateHandler.DEFINITION, impuh);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadWriteAttribute(CHANNELS, readHandler, writeHandler);
    }


    private static final class WriteHandler implements OperationStepHandler {
        private final ParametersValidator validator = new ParametersValidator();
        private final InstMgrService imService;
        public WriteHandler(InstMgrService imService) {
            this.imService = imService;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            // validate ???
            validator.validate(operation);
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    final String name = operation.require(NAME).asString();
                    if (!name.equals(InstMgrConstants.CHANNELS)) {
                        throw unknownAttribute(operation);
                    }
                    try {
                        Optional<InstallationManagerFactory> imOptional = InstallationManagerFinder.find();
                        if (imOptional.isPresent()) {
                            Path serverHome = imService.getHomeDir();

                            MavenOptions mavenOptions = new MavenOptions(null, false);
                            InstallationManager installationManager = imOptional.get().create(serverHome, mavenOptions);

                            final ModelNode mChannel = operation.require(ModelDescriptionConstants.VALUE).get(InstMgrConstants.CHANNEL);
                            final String cName = mChannel.get(InstMgrConstants.CHANNEL_NAME).asString();
                            final List<Repository> repositories = new ArrayList<>();

                            List<ModelNode> mRepositories = mChannel.get(InstMgrConstants.REPOSITORIES).asList();
                            for (ModelNode mRepository : mRepositories) {
                                String id = mRepository.get(InstMgrConstants.REPOSITORY).get(InstMgrConstants.ID).asString();
                                String url = mRepository.get(InstMgrConstants.REPOSITORY).get(InstMgrConstants.REPOSITORY_URL).asString();
                                Repository repository = new Repository(id, url);
                                repositories.add(repository);
                            }

                            String manifestGav;
                            URL manifestUrl;
                            Channel c;
                            if (mChannel.hasDefined(InstMgrConstants.MANIFEST)) {
                                ModelNode mManifest = mChannel.get(InstMgrConstants.MANIFEST);
                                if (mManifest.hasDefined(InstMgrConstants.MANIFEST_GAV)) {
                                    manifestGav = mManifest.get(InstMgrConstants.MANIFEST_GAV).asString();
                                    c = new Channel(cName, repositories, manifestGav);
                                } else {
                                    manifestUrl = new URL(mManifest.get(InstMgrConstants.REPOSITORY_URL).asString());
                                    c = new Channel(cName, repositories, manifestUrl);
                                }
                            } else {
                                c = new Channel(cName, repositories);
                            }

                            installationManager.addChannel(c);

                            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
                        }
                    } catch (Exception e) {
                        throw new OperationFailedException(e);
                    }
                }
            }, OperationContext.Stage.RUNTIME);
        }

        private static OperationFailedException unknownAttribute(final ModelNode operation) {
            return InstMgrLogger.ROOT_LOGGER.unknownAttribute(operation.require(NAME).asString());
        }
    }

    private static final class ReadHandler implements OperationStepHandler {
        private final ParametersValidator validator = new ParametersValidator();
        private final InstMgrService imService;

        public ReadHandler(InstMgrService imService) {
            this.imService = imService;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            // Validate ???
            validator.validate(operation);
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    final String name = operation.require(NAME).asString();
                    if (!name.equals(InstMgrConstants.CHANNELS)) {
                        throw unknownAttribute(operation);
                    }
                    try {
                        Optional<InstallationManagerFactory> imOptional = InstallationManagerFinder.find();
                        if (imOptional.isPresent()) {
                            final ModelNode result = context.getResult();
                            Path serverHome = imService.getHomeDir();

                            MavenOptions mavenOptions = new MavenOptions(null, false);
                            InstallationManager installationManager = imOptional.get().create(serverHome, mavenOptions);

                            ModelNode mChannels = new ModelNode().addEmptyList();
                            Collection<Channel> channels = installationManager.listChannels();
                            for (Channel channel : channels) {
                                ModelNode mChannel = new ModelNode();
                                mChannel.get(InstMgrConstants.CHANNEL_NAME).set(channel.getName());

                                ModelNode mRepositories = new ModelNode().addEmptyList();
                                for (Repository repository : channel.getRepositories()) {
                                    ModelNode mRepository = new ModelNode();
                                    mRepository.get(InstMgrConstants.ID).set(repository.getId());
                                    mRepository.get(InstMgrConstants.REPOSITORY_URL).set(repository.getUrl());
                                    mRepositories.add(mRepository);
                                }
                                mChannel.get(InstMgrConstants.REPOSITORIES).set(mRepositories);

                                ModelNode mManifest = new ModelNode();
                                if (channel.getManifestCoordinate().isPresent()) {
                                    mManifest.get(InstMgrConstants.MANIFEST_GAV).set(channel.getManifestCoordinate().get());
                                    mChannel.get(InstMgrConstants.MANIFEST).set(mManifest);
                                } else if (channel.getManifestUrl().isPresent()) {
                                    mManifest.get(InstMgrConstants.MANIFEST_URL).set(channel.getManifestUrl().get().toExternalForm());
                                    mChannel.get(InstMgrConstants.MANIFEST).set(mManifest);
                                }
                                mChannels.add(mChannel);
                            }
                            result.set(mChannels);
                            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
                        }
                    } catch (Exception e) {
                        throw new OperationFailedException(e);
//                   context.getFailureDescription().set(e.getLocalizedMessage());
                    }
                }
            }, OperationContext.Stage.RUNTIME);
        }

        private static OperationFailedException unknownAttribute(final ModelNode operation) {
            return InstMgrLogger.ROOT_LOGGER.unknownAttribute(operation.require(NAME).asString());
        }
    }
}

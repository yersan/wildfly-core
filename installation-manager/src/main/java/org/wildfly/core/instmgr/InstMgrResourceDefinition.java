/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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

package org.wildfly.core.instmgr;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.ParameterValidator;
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

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;

class InstMgrResourceDefinition extends SimpleResourceDefinition {

    static final String MANAGEMENT_EXECUTOR_CAP = "org.wildfly.management.executor";
    static final String PATH_MANAGER_CAP = "org.wildfly.management.path-manager";
    static final RuntimeCapability<Void> INSTALLATION_MANAGER_CAPABILITY =
            RuntimeCapability.Builder.of("org.wildfly.core.installationmanager", InstMgrResourceDefinition.class)
                    .addRequirements(MANAGEMENT_EXECUTOR_CAP, PATH_MANAGER_CAP)
                    .build();
    private final ReadHandler readHandler;
    private final WriteHandler writeHandler;
    private final InstMgrService imService;

    private static final AttributeDefinition REPOSITORY_ID = new SimpleAttributeDefinitionBuilder(InstMgrConstants.REPOSITORY_ID, ModelType.STRING)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .build();

    private static final AttributeDefinition REPOSITORY_URL = new SimpleAttributeDefinitionBuilder(InstMgrConstants.REPOSITORY_URL, ModelType.STRING)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .build();

    private static final ObjectTypeAttributeDefinition REPOSITORY = new ObjectTypeAttributeDefinition.Builder(InstMgrConstants.REPOSITORY, REPOSITORY_ID, REPOSITORY_URL)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .build();

    private static final AttributeDefinition CHANNEL_REPOSITORIES = new ObjectListAttributeDefinition.Builder(InstMgrConstants.REPOSITORIES, REPOSITORY)
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

    private static final ObjectTypeAttributeDefinition CHANNEL = ObjectTypeAttributeDefinition.create(InstMgrConstants.CHANNEL, CHANNEL_NAME, CHANNEL_REPOSITORIES, CHANNEL_MANIFEST)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .build();

    private static final AttributeDefinition CHANNELS = ObjectListAttributeDefinition.Builder.of(InstMgrConstants.CHANNELS, CHANNEL)
            .setValidator(new ChannelValidator())
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    private final InstallationManagerFactory imf;

    public static PathElement getPath(String name) {
        return PathElement.pathElement(CORE_SERVICE, name);
    }

    public InstMgrResourceDefinition(InstallationManagerFactory imf, InstMgrService imService) {
        super(new Parameters(getPath(InstMgrConstants.TOOL_NAME), InstMgrResolver.RESOLVER)
                .setRuntime()
        );
        this.imf = imf;
        this.imService = imService;
        this.readHandler = new ReadHandler(imService);
        this.writeHandler = new WriteHandler(imService);
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);

        InstMgrHistoryHandler historyHandler = new InstMgrHistoryHandler(imService, imf);
        resourceRegistration.registerOperationHandler(InstMgrHistoryHandler.DEFINITION, historyHandler);

        InstMgrCreateSnapshotHandler createSnapshotHandler = new InstMgrCreateSnapshotHandler(imService, imf);
        resourceRegistration.registerOperationHandler(InstMgrCreateSnapshotHandler.DEFINITION, createSnapshotHandler);

        InstMgrListUpdatesHandler lstUpdatesHandler = new InstMgrListUpdatesHandler(imService, imf);
        resourceRegistration.registerOperationHandler(InstMgrListUpdatesHandler.DEFINITION, lstUpdatesHandler);

        InstMgrCleanHandler clean = new InstMgrCleanHandler(imService, imf);
        resourceRegistration.registerOperationHandler(InstMgrCleanHandler.DEFINITION, clean);

        InstMgrPrepareUpdateHandler prepUpdatesHandler = new InstMgrPrepareUpdateHandler(imService, imf);
        resourceRegistration.registerOperationHandler(InstMgrPrepareUpdateHandler.DEFINITION, prepUpdatesHandler);

        InstMgrPrepareRevertHandler revertHandler = new InstMgrPrepareRevertHandler(imService, imf);
        resourceRegistration.registerOperationHandler(InstMgrPrepareRevertHandler.DEFINITION, revertHandler);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadWriteAttribute(CHANNELS, readHandler, writeHandler);
    }

    private final class WriteHandler implements OperationStepHandler {
        private final InstMgrService imService;

        public WriteHandler(InstMgrService imService) {
            this.imService = imService;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            List<ModelNode> channelsListMn = CHANNELS.resolveValue(context, operation.get(VALUE)).asList();
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    try {
                        Optional<InstallationManagerFactory> imOptional = InstallationManagerFinder.find();
                        if (imOptional.isPresent()) {
                            Path serverHome = imService.getHomeDir();

                            MavenOptions mavenOptions = new MavenOptions(null, false);
                            InstallationManager installationManager = imOptional.get().create(serverHome, mavenOptions);

                            final Collection<Channel> exitingChannels = installationManager.listChannels();
                            final Set<String> exitingChannelNames = new HashSet<>();
                            for (Channel c : exitingChannels) {
                                exitingChannelNames.add(c.getName());
                            }

                            for (ModelNode mChannel : channelsListMn) {
                                final String cName = mChannel.get(InstMgrConstants.CHANNEL_NAME).asString();
                                final List<Repository> repositories = new ArrayList<>();

                                // channel.repositories
                                final List<ModelNode> mRepositories = mChannel.get(InstMgrConstants.REPOSITORIES).asListOrEmpty();
                                for (ModelNode mRepository : mRepositories) {
                                    String id = mRepository.get(InstMgrConstants.REPOSITORY_ID).asString();
                                    String url = mRepository.get(InstMgrConstants.REPOSITORY_URL).asString();
                                    Repository repository = new Repository(id, url);
                                    repositories.add(repository);
                                }

                                // channel.manifest
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

                                if (exitingChannelNames.contains(c.getName())) {
                                    installationManager.changeChannel(c.getName(), c);
                                } else {
                                    installationManager.addChannel(c);
                                }
                            }
                            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }, OperationContext.Stage.RUNTIME);
        }
    }

    private final class ReadHandler implements OperationStepHandler {
        private final InstMgrService imService;

        public ReadHandler(InstMgrService imService) {
            this.imService = imService;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
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
                                    mRepository.get(InstMgrConstants.REPOSITORY_ID).set(repository.getId());
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
                        throw new RuntimeException(e);
                    }
                }
            }, OperationContext.Stage.RUNTIME);
        }
    }


    static class ChannelValidator implements ParameterValidator {
        @Override
        public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
            if (!value.hasDefined("name")) {
                throw new OperationFailedException(String.format("Channel name is mandatory"));
            }
            // validate repositories
            if (!value.hasDefined(InstMgrConstants.REPOSITORIES)) {
                throw new OperationFailedException(String.format("The channel % does not define any repositories"));
            }

            List<ModelNode> repositoriesMn = value.get(InstMgrConstants.REPOSITORIES).asListOrEmpty();
            for (ModelNode repository : repositoriesMn) {
                String repoUrl = repository.get(InstMgrConstants.REPOSITORY_URL).asStringOrNull();
                if (repoUrl == null) {
                    throw new OperationFailedException(String.format("The channel % contains a repository without URL"));
                }
                try {
                    new URL(repoUrl);
                } catch (MalformedURLException e) {
                    throw new OperationFailedException(String.format("The channel % has an invalid repository URL: %s"));
                }
                String repoId = repository.get(InstMgrConstants.REPOSITORY_ID).asStringOrNull();
                if (repoId == null) {
                    throw new OperationFailedException(String.format("The channel % contains a repository without ID"));
                }
            }

            // validate manifest
            if (value.hasDefined(InstMgrConstants.MANIFEST)) {
                String gav = value.get(InstMgrConstants.MANIFEST).get(InstMgrConstants.MANIFEST_GAV).asStringOrNull();
                String url = value.get(InstMgrConstants.MANIFEST).get(InstMgrConstants.MANIFEST_URL).asStringOrNull();

                if (gav != null) {
                    if (gav.contains("\\") || gav.contains("/")) {
                        throw new OperationFailedException(String.format("The channel % has an invalid manifest GAV: %"));
                    }
                    String[] parts = gav.split(":");
                    for (String part : parts) {
                        if (part == null || "".equals(part)) {
                            throw new OperationFailedException(String.format("The channel % has an invalid manifest GAV: %"));
                        }
                    }
                    if (parts.length != 2 && parts.length != 3) { // GA or GAV
                        throw new OperationFailedException(String.format("The channel % has an invalid manifest GAV: %"));
                    }
                }
                if (url != null) {
                    try {
                        new URL(url);
                    } catch (MalformedURLException e) {
                        throw new OperationFailedException(String.format("The channel % has an invalid manifest URL: %"));
                    }
                }
            }
        }
    }
}
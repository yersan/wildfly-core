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

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.core.instmgr.logging.InstMgrLogger;
import org.wildfly.installationmanager.ArtifactChange;
import org.wildfly.installationmanager.Channel;
import org.wildfly.installationmanager.ChannelChange;
import org.wildfly.installationmanager.HistoryResult;
import org.wildfly.installationmanager.InstallationChanges;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.Repository;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

import java.nio.file.Path;
import java.util.List;

public class InstMgrHistoryHandler extends InstMgrOperationStepHandler {
    public static final String OPERATION_NAME = "history";

    private static final AttributeDefinition REVISION = SimpleAttributeDefinitionBuilder.create(InstMgrConstants.REVISION, ModelType.STRING, true)
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, InstMgrResolver.RESOLVER)
            .addParameter(REVISION)
            .withFlags(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
            .setReplyType(ModelType.LIST)
            .setRuntimeOnly()
            .setReplyValueType(ModelType.OBJECT)
            .build();

    InstMgrHistoryHandler(InstMgrService imService, InstallationManagerFactory imf) {
        super(imService, imf);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final String revision = REVISION.resolveModelAttribute(context, operation).asStringOrNull();
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                try {

                    // @TODO Change the Handler and return a complex object instead of the lines expected. That will allow to any client to print the output as they want.

                    Path serverHome = imService.getHomeDir();
                    MavenOptions mavenOptions = new MavenOptions(null, false);
                    InstallationManager installationManager = imf.create(serverHome, mavenOptions);
                    ModelNode resulList = new ModelNode().addEmptyList();
                    if (revision == null) {
                        List<HistoryResult> history = installationManager.history();
                        for (HistoryResult hr : history) {
                            ModelNode entry = new ModelNode();
                            entry.get(InstMgrConstants.HASH).set(hr.getName());
                            entry.get(InstMgrConstants.TIMESTAMP).set(hr.timestamp().toString());
                            entry.get(InstMgrConstants.TYPE).set(hr.getType().toLowerCase());
                            resulList.add(entry);
                        }
                    } else {
                        InstallationChanges changes = installationManager.revisionDetails(revision);
                        List<ArtifactChange> artifactChanges = changes.artifactChanges();
                        List<ChannelChange> channelChanges = changes.channelChanges();

                        if (!artifactChanges.isEmpty()) {
                            resulList.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_HISTORY_ARTIFACTS_UPDATES)));
                            for (ArtifactChange artifactChange : artifactChanges) {
                                switch (artifactChange.getStatus()) {
                                    case REMOVED:
                                        resulList.add(String.format("%1$-65s %2$-15s ==> []", artifactChange.getArtifactName(), artifactChange.getOldVersion()));
                                        break;
                                    case INSTALLED:
                                        resulList.add(String.format("%1$-65s [] ==> %2$-15s", artifactChange.getArtifactName(), artifactChange.getNewVersion()));
                                        break;
                                    case UPDATED:
                                        resulList.add(String.format("%1$-65s %2$-15s ==> %3$-15s", artifactChange.getArtifactName(), artifactChange.getOldVersion(), artifactChange.getNewVersion()));
                                        break;
                                    default:
                                        InstMgrLogger.ROOT_LOGGER.unexpectedArtifactChange(artifactChange.toString());
                                }
                            }
                            resulList.add("\n");
                        }

                        if (!channelChanges.isEmpty()) {
                            resulList.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_HISTORY_CONFIGURATION_CHANGES)));
                            for (ChannelChange channelChange : channelChanges) {
                                switch (channelChange.getStatus()) {
                                    case REMOVED: {
                                        Channel channel = channelChange.getOldChannel().get();
                                        String name = channel.getName();
                                        resulList.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_REMOVED_CHANNEL), name));

                                        String manifest = getManifest(channel);
                                        if (!"".equals(manifest)) {
                                            resulList.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_REMOVED_CHANNEL_MANIFEST), manifest));
                                        }

                                        List<Repository> repositories = channel.getRepositories();
                                        resulList.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_REPOSITORIES)));
                                        for (Repository repository : repositories) {
                                            resulList.add(String.format("\t\t%s ==> []", repository.asFormattedString()));
                                        }
                                        break;
                                    }
                                    case ADDED: {
                                        Channel channel = channelChange.getNewChannel().get();
                                        String name = channel.getName();
                                        resulList.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_ADDED_CHANNEL), name));

                                        String manifest = getManifest(channel);
                                        if (!"".equals(manifest)) {
                                            resulList.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_ADDED_CHANNEL_MANIFEST), manifest));
                                        }

                                        List<Repository> repositories = channel.getRepositories();
                                        resulList.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_REPOSITORIES)));
                                        for (Repository repository : repositories) {
                                            resulList.add(String.format("\t\t[] ==> %s", repository.asFormattedString()));
                                        }
                                        break;
                                    }
                                    case MODIFIED: {
                                        // @TODO Do we have channel modifications? There is no command for it on Prospero side
                                        Channel oldChannel = channelChange.getOldChannel().get();
                                        Channel newChannel = channelChange.getNewChannel().get();

                                        resulList.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_UPDATED_CHANNEL), oldChannel.getName()));

                                        String oldManifest = getManifest(oldChannel);
                                        String newManifest = getManifest(newChannel);

                                        if (!"".equals(oldManifest) || !"".equals(newManifest)) {
                                            String oldManifestPrintable = "".equals(oldManifest) ? "[]" : oldManifest;
                                            String newManifestPrintable = "".equals(newManifest) ? "[]" : newManifest;
                                            resulList.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_UPDATED_CHANNEL_MANIFEST), oldManifestPrintable, newManifestPrintable));
                                        }

                                        resulList.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_REPOSITORIES)));
                                        List<Repository> oldRepositoriesLst = oldChannel.getRepositories();
                                        for (Repository repository : oldRepositoriesLst) {
                                            resulList.add(String.format("\t\t%s ==> []", repository.asFormattedString()));
                                        }

                                        List<Repository> newRepositoriesLst = newChannel.getRepositories();
                                        for (Repository repository : newRepositoriesLst) {
                                            resulList.add(String.format("\t\t[] ==> %s", repository.asFormattedString()));
                                        }
                                        break;
                                    }
                                    default: {
                                        InstMgrLogger.ROOT_LOGGER.unexpectedConfigurationChange(channelChange.toString());
                                    }
                                }
                            }
                        }
                    }

                    if (resulList.asListOrEmpty().isEmpty()) {
                        resulList.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_NO_UPDATES_FOUND)));
                    }
                    context.getResult().set(resulList);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }, OperationContext.Stage.RUNTIME);
    }

    private String getManifest(Channel channel) {
        String manifest = "";
        if (channel.getManifestUrl().isPresent()) {
            manifest = channel.getManifestUrl().get().toString();
        } else if (channel.getManifestCoordinate().isPresent()) {
            manifest = channel.getManifestCoordinate().get();
        }
        return manifest;
    }
}

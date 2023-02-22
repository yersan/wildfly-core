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
import org.jboss.as.controller.ObjectListAttributeDefinition;
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
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.Repository;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipException;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTACHED_STREAMS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FILESYSTEM_PATH;

/**
 * Operation Handler that list the components that can be updated.
 */
public class InstMgrListUpdatesHandler extends AbstractInstMgrUpdateHandler {
    static final String OPERATION_NAME = "list-updates";
    protected static final AttributeDefinition MAVEN_REPO_FILE = SimpleAttributeDefinitionBuilder.create(InstMgrConstants.MAVEN_REPO_FILE, ModelType.INT)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(false)
            .addArbitraryDescriptor(FILESYSTEM_PATH, ModelNode.TRUE)
            .addArbitraryDescriptor(ATTACHED_STREAMS, ModelNode.TRUE)
            .setAlternatives(InstMgrConstants.REPOSITORIES)
            .build();

    protected static final AttributeDefinition REPOSITORIES = new ObjectListAttributeDefinition.Builder(InstMgrConstants.REPOSITORIES, REPOSITORY)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(false)
            .setAlternatives(InstMgrConstants.MAVEN_REPO_FILE)
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, InstMgrResolver.RESOLVER)
            .addParameter(OFFLINE)
            .addParameter(REPOSITORIES)
            .addParameter(LOCAL_CACHE)
            .addParameter(NO_RESOLVE_LOCAL_CACHE)
            .addParameter(MAVEN_REPO_FILE)
            .withFlags(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
            .setRuntimeOnly()
            .build();

    public InstMgrListUpdatesHandler(InstMgrService imService, InstallationManagerFactory imf) {
        super(imService, imf);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final boolean offline = OFFLINE.resolveModelAttribute(context, operation).asBoolean(false);
        final String pathLocalRepo = LOCAL_CACHE.resolveModelAttribute(context, operation).asStringOrNull();
        final boolean noResolveLocalCache = NO_RESOLVE_LOCAL_CACHE.resolveModelAttribute(context, operation).asBoolean(false);
        final Path localRepository = pathLocalRepo != null ? Path.of(pathLocalRepo) : null;
        final Integer mavenRepoFileIndex = MAVEN_REPO_FILE.resolveModelAttribute(context, operation).asIntOrNull();
        final List<ModelNode> repositoriesMn = REPOSITORIES.resolveModelAttribute(context, operation).asListOrEmpty();

        if (pathLocalRepo != null && noResolveLocalCache) {
            throw InstMgrLogger.ROOT_LOGGER.localCacheWithNoResolveLocalCache();
        }

        if (mavenRepoFileIndex != null && !repositoriesMn.isEmpty()) {
            throw InstMgrLogger.ROOT_LOGGER.mavenRepoFileWithRepositories();
        }

        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                context.acquireControllerLock();
                try {
                    final Path homeDir = imService.getHomeDir();
                    final MavenOptions mavenOptions = new MavenOptions(localRepository, noResolveLocalCache, offline);
                    final InstallationManager im = imf.create(homeDir, mavenOptions);
                    final Path listUpdatesWorkDir = imService.createTempDir("list-updates-");

                    context.completeStep(new OperationContext.ResultHandler() {
                        @Override
                        public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                            if (resultAction == OperationContext.ResultAction.ROLLBACK) {
                                try {
                                    imService.deleteTempDir(listUpdatesWorkDir);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    });

                    final List<Repository> repositories;
                    if (mavenRepoFileIndex != null) {
                        final InputStream is = context.getAttachmentStream(mavenRepoFileIndex);
                        Path uploadedRepoZipFile = listUpdatesWorkDir.resolve("maven-repo-list-updates.zip");
                        Files.copy(is, uploadedRepoZipFile);
                        // @TODO Understand how the zip files are packed, we need to specify the root of the file as the repository. Need to understand if this maven repository is packaged in a subdirectory
                        unzip(uploadedRepoZipFile.toFile(), listUpdatesWorkDir.toFile());

                        Path uploadedMvnRepoRoot = getUploadedMvnRepoRoot(listUpdatesWorkDir);
                        Repository uploadedMavenRepo = new Repository("id0", uploadedMvnRepoRoot.toUri().toString());
                        repositories = List.of(uploadedMavenRepo);
                    } else {
                        repositories = toRepositories(repositoriesMn);
                    }

                    final List<ArtifactChange> updates = im.findUpdates(repositories);
                    final ModelNode resultValue = new ModelNode();
                    final ModelNode updatesMn = new ModelNode().addEmptyList();

                    if (!updates.isEmpty()) {
                        updatesMn.add(InstMgrResolver.getString(InstMgrResolver.KEY_UPDATES_FOUND));
                        for (ArtifactChange artifactChange : updates) {
                            switch (artifactChange.getStatus()) {
                                case REMOVED:
                                    updatesMn.add(String.format("    %1$-60s %2$-15s ==> []", artifactChange.getArtifactName(), artifactChange.getOldVersion()));
                                    break;
                                case INSTALLED:
                                    updatesMn.add(String.format("    %1$-60s [] ==> %2$-15s", artifactChange.getArtifactName(), artifactChange.getNewVersion()));
                                    break;
                                default:
                                    updatesMn.add(String.format("    %1$-60s %2$-15s ==> %3$-15s", artifactChange.getArtifactName(), artifactChange.getOldVersion(), artifactChange.getNewVersion()));
                            }
                        }
                        if (mavenRepoFileIndex != null) {
                            resultValue.get(InstMgrConstants.RETURN_CODE).set(InstMgrConstants.RETURN_CODE_UPDATES_WITH_WORK_DIR);
                            resultValue.get(InstMgrConstants.UPDATES_RESULT).set(updatesMn);
                            resultValue.get(InstMgrConstants.LIST_UPDATES_WORK_DIR).set(listUpdatesWorkDir.getFileName().toString());
                        } else {
                            imService.deleteTempDir(listUpdatesWorkDir);
                            resultValue.get(InstMgrConstants.RETURN_CODE).set(InstMgrConstants.RETURN_CODE_UPDATES_WITHOUT_WORK_DIR);
                            resultValue.get(InstMgrConstants.UPDATES_RESULT).set(updatesMn);
                        }
                    } else {
                        imService.deleteTempDir(listUpdatesWorkDir);
                        updatesMn.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_NO_UPDATES_FOUND)));
                        resultValue.get(InstMgrConstants.RETURN_CODE).set(InstMgrConstants.RETURN_CODE_NO_UPDATES);
                        resultValue.get(InstMgrConstants.UPDATES_RESULT).set(updatesMn);
                    }

                    context.getResult().set(resultValue);

                    // @TODO Better Exception handling
                } catch (ZipException e) {
                    context.getFailureDescription().set(e.getLocalizedMessage());
                    throw new OperationFailedException(e);
                } catch (RuntimeException e) {
                    throw e;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }, OperationContext.Stage.RUNTIME);
    }
}

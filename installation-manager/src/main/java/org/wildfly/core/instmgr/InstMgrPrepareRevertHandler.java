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
 * Operation handler that prepare a candidate server reverted to a previous installation state.
 */
public class InstMgrPrepareRevertHandler extends AbstractInstMgrUpdateHandler {

    public static final String OPERATION_NAME = "prepare-revert";

    private static final AttributeDefinition REVISION = SimpleAttributeDefinitionBuilder.create(InstMgrConstants.REVISION, ModelType.STRING)
            .setRequired(true)
            .build();

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
            .addParameter(REVISION)
            .addParameter(OFFLINE)
            .addParameter(REPOSITORIES)
            .addParameter(LOCAL_CACHE)
            .addParameter(NO_RESOLVE_LOCAL_CACHE)
            .addParameter(MAVEN_REPO_FILE)
            .withFlags(OperationEntry.Flag.HOST_CONTROLLER_ONLY, OperationEntry.Flag.HIDDEN)
            .setRuntimeOnly()
            .build();

    InstMgrPrepareRevertHandler(InstMgrService imService, InstallationManagerFactory imf) {
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
        final String revision = REVISION.resolveModelAttribute(context, operation).asString();

        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                context.acquireControllerLock();
                if (!imService.canPrepareServer()) {
                    throw InstMgrLogger.ROOT_LOGGER.serverAlreadyPrepared();
                }
                imService.beginCandidateServer();
                addCompleteStep(context, imService, null);

                try {
                    final Path homeDir = imService.getHomeDir();
                    final MavenOptions mavenOptions = new MavenOptions(localRepository, noResolveLocalCache, offline);
                    final InstallationManager im = imf.create(homeDir, mavenOptions);

                    List<Repository> repositories;
                    if (mavenRepoFileIndex != null) {
                        final InputStream is = context.getAttachmentStream(mavenRepoFileIndex);
                        final Path preparationWorkDir = imService.createTempDir("prepare-revert-");
                        addCompleteStep(context, imService, preparationWorkDir.getFileName().toString());

                        Path uploadedRepoZipFileName = preparationWorkDir.resolve("maven-repo-prepare-updates.zip");
                        Files.copy(is, uploadedRepoZipFileName);
                        // @TODO Understand how the zip files are packed, we need to specify the root of the file as the repository. Need to understand if this maven repository is packaged in a subdirectory
                        unzip(uploadedRepoZipFileName.toFile(), preparationWorkDir.toFile());

                        Path uploadedRepoZipRootDir = getUploadedMvnRepoRoot(preparationWorkDir);
                        Repository uploadedMavenRepo = new Repository("id0", uploadedRepoZipRootDir.toUri().toString());
                        repositories = List.of(uploadedMavenRepo);
                    } else {
                        repositories = toRepositories(repositoriesMn);
                    }

                    Files.createDirectories(imService.getPreparedServerDir());
                    im.prepareRevert(revision, imService.getPreparedServerDir(), repositories);
                    context.getResult().set(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_CANDIDATE_SERVER_PREPARED_AT), imService.getPreparedServerDir().normalize().toAbsolutePath()));
                    imService.commitCandidateServer("prospero.sh", "revert apply");

                    // @TODO Better Exception handling
                } catch (ZipException e) {
                    context.getFailureDescription().set(e.getLocalizedMessage());
                    throw new OperationFailedException(e);
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

    private void addCompleteStep(OperationContext context, InstMgrService imService, String mavenRepoParentWorkdir) {
        context.completeStep(new OperationContext.ResultHandler() {
            @Override
            public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                try {
                    imService.deleteTempDir(mavenRepoParentWorkdir);
                    if (resultAction == OperationContext.ResultAction.ROLLBACK) {
                        imService.resetCandidateStatus();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }
}
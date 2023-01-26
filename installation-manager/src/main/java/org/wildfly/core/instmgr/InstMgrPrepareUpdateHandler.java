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
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.domain.http.server.OperatingSystemDetector;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.core.instmgr.logging.InstMgrLogger;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.Repository;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipException;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTACHED_STREAMS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FILESYSTEM_PATH;

public class InstMgrPrepareUpdateHandler extends AbstractInstMgrUpdateHandler {
    static final String OPERATION_NAME = "prepare-updates";

    protected static final AttributeDefinition MAVEN_REPO_FILE = SimpleAttributeDefinitionBuilder.create(InstMgrConstants.MAVEN_REPO_FILE, ModelType.INT)
            .setRequired(false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .addArbitraryDescriptor(FILESYSTEM_PATH, ModelNode.TRUE)
            .addArbitraryDescriptor(ATTACHED_STREAMS, ModelNode.TRUE)
            .setAlternatives(InstMgrConstants.LIST_UPDATES_WORK_DIR, InstMgrConstants.REPOSITORIES)
            .build();

    protected static final AttributeDefinition LIST_UPDATES_WORK_DIR = SimpleAttributeDefinitionBuilder.create(InstMgrConstants.LIST_UPDATES_WORK_DIR, ModelType.STRING)
            .setRequired(false)
            .setStorageRuntime()
            .addArbitraryDescriptor(FILESYSTEM_PATH, ModelNode.TRUE)
            .setAlternatives(InstMgrConstants.MAVEN_REPO_FILE, InstMgrConstants.REPOSITORIES)
            .build();

    protected static final AttributeDefinition REPOSITORIES = new ObjectTypeAttributeDefinition.Builder(InstMgrConstants.REPOSITORIES, REPOSITORY)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(false)
            .setAlternatives(InstMgrConstants.LIST_UPDATES_WORK_DIR, InstMgrConstants.MAVEN_REPO_FILE)
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, InstMgrResolver.RESOLVER)
            .addParameter(OFFLINE)
            .addParameter(REPOSITORIES)
            .addParameter(LOCAL_CACHE)
            .addParameter(NO_RESOLVE_LOCAL_CACHE)
            .addParameter(MAVEN_REPO_FILE)
            .addParameter(LIST_UPDATES_WORK_DIR)
            .withFlag(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
            .setRuntimeOnly()
            .build();

    InstMgrPrepareUpdateHandler(InstMgrService imService, InstallationManagerFactory imf) {
        super(imService, imf);
    }

    @Override
    void executeRuntimeStep(OperationContext context, ModelNode operation, InstMgrService imService, InstallationManagerFactory imf) throws OperationFailedException {
        context.acquireControllerLock();

        final boolean offline = resolveAttribute(context, operation, OFFLINE).isDefined() ? resolveAttribute(context, operation, OFFLINE).asBoolean() : false;
        final String pathLocalRepo = resolveAttribute(context, operation, LOCAL_CACHE).asStringOrNull();
        final boolean noResolveLocalCache = resolveAttribute(context, operation, NO_RESOLVE_LOCAL_CACHE).isDefined() ? resolveAttribute(context, operation, NO_RESOLVE_LOCAL_CACHE).asBoolean() : false;
        final Path localRepository = pathLocalRepo != null ? Path.of(pathLocalRepo) : null;
        final Integer mavenRepoFileIndex = resolveAttribute(context, operation, MAVEN_REPO_FILE).asIntOrNull();
        final String listUpdatesWorkDir = resolveAttribute(context, operation, LIST_UPDATES_WORK_DIR).asStringOrNull();
        final ModelNode repositoriesMn = operation.hasDefined(REPOSITORIES.getName()) ? REPOSITORIES.resolveModelAttribute(context, operation).asObject() : null;

        if (imService.isServerPrepared()) {
            throw InstMgrLogger.ROOT_LOGGER.serverAlreadyPrepared();
        }
        imService.setServerPrepared(true);
        addCompleteStep(context, imService, null);

        try {
            final Path homeDir = imService.getHomeDir();
            final MavenOptions mavenOptions = new MavenOptions(localRepository, noResolveLocalCache, offline);
            final InstallationManager im = imf.create(homeDir, mavenOptions);

            List<Repository> repositories;
            if (listUpdatesWorkDir != null) {
                // We come from list-updates and there is already a maven repo unzipped
                final Path mvnRepoWorkDir = imService.getTrackedWorkDirByName(listUpdatesWorkDir);
                // @TODO: check that mvnRepoParentWorkdir exists?
                addCompleteStep(context, imService, listUpdatesWorkDir);

                Path uploadedRepoZipRootDir = getUploadedMvnRepoRoot(mvnRepoWorkDir);
                Repository uploadedMavenRepo = new Repository("id0", uploadedRepoZipRootDir.toUri().toString());
                repositories = List.of(uploadedMavenRepo);

            } else if (mavenRepoFileIndex != null) {
                final InputStream is = context.getAttachmentStream(mavenRepoFileIndex);
                final Path prepareUpdateWorkDir = imService.createWorkDir("prepare-updates-");

                addCompleteStep(context, imService, prepareUpdateWorkDir.getFileName().toString());

                Path uploadedRepoZipFileName = prepareUpdateWorkDir.resolve("maven-repo-prepare-updates.zip");
                Files.copy(is, uploadedRepoZipFileName);
                // @TODO Understand how the zip files are packed, we need to specify the root of the file as the repository. Need to understand if this maven repository is packaged in a subdirectory
                unzip(uploadedRepoZipFileName.toFile(), prepareUpdateWorkDir.toFile());

                Path uploadedRepoZipRootDir = getUploadedMvnRepoRoot(prepareUpdateWorkDir);
                Repository uploadedMavenRepo = new Repository("id0", uploadedRepoZipRootDir.toUri().toString());
                repositories = List.of(uploadedMavenRepo);
            } else {
                repositories = toRepositories(repositoriesMn);
            }

            Files.createDirectories(imService.getPreparedServerDir());
            im.prepareUpdate(imService.getPreparedServerDir(), repositories);

            // @TODO: This is expected to be generated by Prospero itself.
            // We need to replace this installation-manager-lib.sh file and get the prospero installation
            // command by using a main class on the prospero module.
            // the main class should accept the two arguments --dir and --update-dir and the command to generate update apply or update revert
            if (!OperatingSystemDetector.INSTANCE.isWindows()) {
                Path prosperoFunction = imService.getApplyUpdatesCliScript();

                Files.deleteIfExists(prosperoFunction);
                Files.createFile(prosperoFunction);

                try (FileWriter fw = new FileWriter(prosperoFunction.toFile());
                     BufferedWriter bw = new BufferedWriter(fw);
                     PrintWriter out = new PrintWriter(bw)) {

                    out.println("function run_installation_manager() {");
                    out.println("\"${JBOSS_HOME}/bin/prospero.sh\" update apply --dir=\"${JBOSS_HOME}\" --update-dir=\"" + imService.getPreparedServerDir() + "\"");
                    out.println("}");
                }
            } else {
                // Windows bat not available
            }

            // put server in restart required?
            // once put in restart required, the clean operation should revert it if it cleans the prepared server
            // but we cannot revert the restart from a different Operation
//            context.restartRequired();
            context.getResult().set("Candidate Server prepared at " + imService.getPreparedServerDir().normalize().toAbsolutePath());
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

    private void addCompleteStep(OperationContext context, InstMgrService imService, String workDir) {
        context.completeStep(new OperationContext.ResultHandler() {
            @Override
            public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                try {
                    imService.cleanTrackedWorkDir(workDir);
                    if (resultAction == OperationContext.ResultAction.ROLLBACK) {
                        imService.setServerPrepared(false);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }
}

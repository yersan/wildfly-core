package org.wildfly.core.instmgr;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
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

    protected static final AttributeDefinition REPOSITORIES = new ObjectTypeAttributeDefinition.Builder(InstMgrConstants.REPOSITORIES, REPOSITORY)
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
            .withFlag(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
            .setRuntimeOnly()
            .build();

    public InstMgrListUpdatesHandler(InstMgrService imService, InstallationManagerFactory imf) {
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
        final ModelNode repositoriesMn = operation.hasDefined(REPOSITORIES.getName()) ? REPOSITORIES.resolveModelAttribute(context, operation).asObject() : null;

        try {
            final Path homeDir = imService.getHomeDir();
            final Path workDir = imService.getWorkdir();
            final MavenOptions mavenOptions = new MavenOptions(localRepository, noResolveLocalCache, offline);
            final InstallationManager im = imf.create(homeDir, mavenOptions);
            final Path listUpdatesWorkDir = Files.createTempDirectory(workDir, "list-updates");
            context.completeStep(new OperationContext.ResultHandler() {
                @Override
                public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                    if (resultAction == OperationContext.ResultAction.ROLLBACK) {
                        try {
                            deleteDirIfExits(listUpdatesWorkDir);
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
            ModelNode resultValue = new ModelNode();
            ModelNode updatesMn = new ModelNode().addEmptyList();
            if (!updates.isEmpty()) {
                for (ArtifactChange artifactChange : updates) {
                    switch (artifactChange.getStatus()) {
                        case REMOVED:
                            updatesMn.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_REMOVED_ARTIFACT), artifactChange.getOldGav(), artifactChange.getOldVersion()));
                            break;
                        case INSTALLED:
                            updatesMn.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_INSTALLED_ARTIFACT), artifactChange.getNewGav(), artifactChange.getNewVersion()));
                            break;
                        default:
                            updatesMn.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_UPDATED_ARTIFACT), artifactChange.getOldVersion(), artifactChange.getOldGav(), artifactChange.getNewGav()));
                    }
                }
                if (mavenRepoFileIndex != null) {
                    resultValue.get(InstMgrConstants.RETURN_CODE).set(0);
                    resultValue.get(InstMgrConstants.UPDATES_RESULT).set(updatesMn);
                    resultValue.get(InstMgrConstants.LIST_UPDATES_WORK_DIR).set(listUpdatesWorkDir.normalize().toAbsolutePath().toString());
                } else {
                    deleteDirIfExits(listUpdatesWorkDir);
                    resultValue.get(InstMgrConstants.RETURN_CODE).set(1);
                    resultValue.get(InstMgrConstants.UPDATES_RESULT).set(updatesMn);
                }
            } else {
                deleteDirIfExits(listUpdatesWorkDir);
                updatesMn.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_NO_CHANGES_FOUND)));
                resultValue.get(InstMgrConstants.RETURN_CODE).set(2);
                resultValue.get(InstMgrConstants.UPDATES_RESULT).set(updatesMn);
            }

            context.getResult().set(resultValue);

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
}

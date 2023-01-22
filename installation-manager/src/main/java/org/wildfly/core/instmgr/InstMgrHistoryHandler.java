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
import org.wildfly.installationmanager.ArtifactChange;
import org.wildfly.installationmanager.Channel;
import org.wildfly.installationmanager.ChannelChange;
import org.wildfly.installationmanager.HistoryResult;
import org.wildfly.installationmanager.InstallationChanges;
import org.wildfly.installationmanager.InstallationManagerFinder;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.Repository;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class InstMgrHistoryHandler implements OperationStepHandler {
    public static final String OPERATION_NAME = "history";

    private static final AttributeDefinition REVISION = SimpleAttributeDefinitionBuilder.create(InstMgrConstants.REVISION, ModelType.STRING, true)
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, InstMgrResolver.RESOLVER)
            .addParameter(REVISION)
            .withFlag(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
            .setRuntimeOnly()
            .build();
    private final InstMgrService imService;


    public InstMgrHistoryHandler(InstMgrService imService) {
        this.imService = imService;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final String revision = REVISION.resolveModelAttribute(context, operation).asStringOrNull();
        context.addStep(new OperationStepHandler() {

            @Override
            public void execute(OperationContext context, ModelNode operation) {
                try {
                    Optional<InstallationManagerFactory> imOptional = InstallationManagerFinder.find();
                    if (imOptional.isPresent()) {
                        Path serverHome = imService.getHomeDir();
                        MavenOptions mavenOptions = new MavenOptions(null, false);
                        InstallationManager installationManager = imOptional.get().create(serverHome, mavenOptions);
                        ModelNode resulList = new ModelNode().addEmptyList();

                        if (revision == null) {
                            List<HistoryResult> history = installationManager.history();
                            for (HistoryResult hr : history) {
                                resulList.add(String.format("[%s] %s - %s", hr.getName(), hr.timestamp().toString(), hr.getType().toLowerCase()));
                            }
                        } else {
                            InstallationChanges changes = installationManager.revisionDetails(revision);
                            List<ArtifactChange> artifactChanges = changes.artifactChanges();
                            List<ChannelChange> channelChanges = changes.channelChanges();

                            if (!artifactChanges.isEmpty() || !channelChanges.isEmpty()) {

                                for (ArtifactChange artifactChange : artifactChanges) {
                                    switch (artifactChange.getStatus()) {
                                        case REMOVED:
                                            resulList.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_REMOVED_ARTIFACT), artifactChange.getOldGav(), artifactChange.getOldVersion()));
                                            break;
                                        case INSTALLED:
                                            resulList.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_INSTALLED_ARTIFACT), artifactChange.getNewGav(), artifactChange.getNewVersion()));
                                            break;
                                        default:
                                            resulList.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_UPDATED_ARTIFACT), artifactChange.getOldVersion(), artifactChange.getOldGav(), artifactChange.getNewGav()));
                                    }
                                }

                                for (ChannelChange channelChange : channelChanges) {
                                    switch (channelChange.getStatus()) {
                                        case REMOVED: {
                                            Channel channel = channelChange.getOldChannel().get();
                                            String name = channel.getName();
                                            resulList.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_REMOVED_CHANNEL), name));
                                            resulList.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_REMOVED_CHANNEL_NAME), name));

                                            String manifest = getManifest(channel);
                                            resulList.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_REMOVED_CHANNEL_MANIFEST), manifest));

                                            List<Repository> repositories = channel.getRepositories();
                                            for (Repository repository : repositories) {
                                                resulList.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_REMOVED_CHANNEL_REPOSITORY), repository.asFormattedString()));
                                            }
                                            break;
                                        }
                                        case ADDED: {
                                            Channel channel = channelChange.getNewChannel().get();
                                            String name = channel.getName();
                                            resulList.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_ADDED_CHANNEL), name));
                                            resulList.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_ADDED_CHANNEL_NAME), name));

                                            String manifest = getManifest(channel);
                                            resulList.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_ADDED_CHANNEL_MANIFEST), manifest));

                                            List<Repository> repositories = channel.getRepositories();
                                            for (Repository repository : repositories) {
                                                resulList.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_ADDED_CHANNEL_REPOSITORY), repository.asFormattedString()));
                                            }
                                            break;
                                        }
                                        default: {
                                            // @TODO Do we have channel modifications? There is no command for it on Prospero side
                                            Channel oldChannel = channelChange.getOldChannel().get();
                                            Channel newChannel = channelChange.getNewChannel().get();

                                            resulList.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_UPDATED_CHANNEL), oldChannel.getName()));
                                            resulList.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_UPDATED_CHANNEL_NAME), oldChannel.getName(), newChannel.getName()));


                                            String oldManifest = getManifest(oldChannel);
                                            String newManifest = getManifest(newChannel);
                                            resulList.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_UPDATED_CHANNEL_MANIFEST), oldManifest, newManifest));

                                            List<Repository> oldRepositoriesLst = oldChannel.getRepositories();
                                            List<String> oldRepositories = oldRepositoriesLst.stream()
                                                    .map(p -> p.asFormattedString())
                                                    .collect(Collectors.toList());

                                            List<Repository> newRepositoriesLst = newChannel.getRepositories();
                                            List<String> newRepositories = newRepositoriesLst.stream()
                                                    .map(p -> p.asFormattedString())
                                                    .collect(Collectors.toList());
                                            resulList.add(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_UPDATED_CHANNEL_REPOSITORY), String.join(",", oldRepositories), String.join(",", newRepositories)));
                                        }
                                    }
                                }
                            } else {
                                resulList = new ModelNode(String.format(InstMgrResolver.getString(InstMgrResolver.KEY_NO_CHANGES_FOUND)));
                            }
                        }
                        context.getResult().set(resulList);
                    }
                } catch (Exception e) {
                    context.getFailureDescription().set(e.getLocalizedMessage());
                }
                context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
            }
        }, OperationContext.Stage.RUNTIME);
    }

    private static String getManifest(Channel channel) {
        String manifest;
        if (channel.getManifestUrl().isPresent()) {
            manifest = channel.getManifestUrl().get().toString();
        } else {
            manifest = channel.getManifestCoordinate().get();
        }
        return manifest;
    }
}

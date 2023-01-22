package org.wildfly.core.instmgr;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.installationmanager.InstallationManagerFinder;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FILESYSTEM_PATH;

public class InstMgrCreateSnapshotHandler implements OperationStepHandler {
    public static final String OPERATION_NAME = "create-snapshot";

    private static final AttributeDefinition PATH =
            new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.PATH, ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
                    .addArbitraryDescriptor(FILESYSTEM_PATH, ModelNode.TRUE)
                    .setRequired(true)
                    .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, InstMgrResolver.RESOLVER)
            .addParameter(PATH)
            .withFlag(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
            .setRuntimeOnly()
            .build();
    private final InstMgrService imService;

    public InstMgrCreateSnapshotHandler(InstMgrService imService) {
        this.imService = imService;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final String exportPath = PATH.resolveModelAttribute(context, operation).asStringOrNull();
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                try {
                    Optional<InstallationManagerFactory> imOptional = InstallationManagerFinder.find();
                    if (imOptional.isPresent()) {
                        Path serverHome = imService.getHomeDir();
                        MavenOptions mavenOptions = new MavenOptions(null, false);
                        InstallationManager installationManager = imOptional.get().create(serverHome, mavenOptions);
                        Path snapshot = installationManager.createSnapshot(Paths.get(exportPath));
                        context.getResult().set(new ModelNode(snapshot.toString()));
                    }
                } catch (Exception e) {
                    context.getFailureDescription().set(e.getLocalizedMessage());
                }
                context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
            }
        }, OperationContext.Stage.RUNTIME);
    }
}

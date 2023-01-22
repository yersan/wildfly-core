package org.wildfly.core.instmgr;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

import java.nio.file.Path;

public class InstMgrCleanHandler extends InstMgrOperationStepHandler {
    static final String OPERATION_NAME = "clean";

    protected static final AttributeDefinition LIST_UPDATES_WORK_DIR = SimpleAttributeDefinitionBuilder.create(InstMgrConstants.LIST_UPDATES_WORK_DIR, ModelType.STRING)
            .setRequired(false)
            .setStorageRuntime()
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, InstMgrResolver.RESOLVER)
            .withFlag(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
            .addParameter(LIST_UPDATES_WORK_DIR)
            .setRuntimeOnly()
            .build();

    InstMgrCleanHandler(InstMgrService imService, InstallationManagerFactory imf) {
        super(imService, imf);
    }

    @Override
    void executeRuntimeStep(OperationContext context, ModelNode operation, InstMgrService imService, InstallationManagerFactory imf) throws OperationFailedException {
        context.getServiceRegistry(true);
        final String listUpdatesWorkDir = resolveAttribute(context, operation, LIST_UPDATES_WORK_DIR).asStringOrNull();
        try {
            final Path workDir = imService.getWorkdir();
            if (listUpdatesWorkDir != null) {
                // delete specifically
                Path mavenRepoDir = workDir.resolve(listUpdatesWorkDir);
                deleteDirIfExits(mavenRepoDir);
            } else {
                // clean all
                deleteDirIfExits(workDir, true);
            }
            imService.setServerPrepared(false);
        } catch (Exception e) {
            context.getFailureDescription().set(e.getLocalizedMessage());
            throw new RuntimeException(e);
        }
    }
}

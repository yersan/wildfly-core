package org.wildfly.core.instmgr;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.installationmanager.Repository;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FILESYSTEM_PATH;

public abstract class AbstractInstMgrUpdateHandler extends InstMgrOperationStepHandler {
    protected static final AttributeDefinition OFFLINE = SimpleAttributeDefinitionBuilder.create(InstMgrConstants.OFFLINE, ModelType.BOOLEAN)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setDefaultValue(ModelNode.FALSE)
            .setRequired(false)
            .build();
    protected static final AttributeDefinition REPOSITORY_ID = new SimpleAttributeDefinitionBuilder(InstMgrConstants.ID, ModelType.STRING)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();
    protected static final AttributeDefinition REPOSITORY_URL = new SimpleAttributeDefinitionBuilder(InstMgrConstants.REPOSITORY_URL, ModelType.STRING)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();
    protected static final AttributeDefinition REPOSITORY = new ObjectTypeAttributeDefinition.Builder(InstMgrConstants.REPOSITORY, REPOSITORY_ID, REPOSITORY_URL)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(false)
            .build();
    protected static final SimpleAttributeDefinition LOCAL_CACHE = new SimpleAttributeDefinitionBuilder(InstMgrConstants.LOCAL_CACHE, ModelType.STRING)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setAllowExpression(true)
            .addArbitraryDescriptor(FILESYSTEM_PATH, ModelNode.TRUE)
            .setMinSize(1)
            .setRequired(false)
            .setAlternatives(InstMgrConstants.NO_RESOLVE_LOCAL_CACHE)
            .build();
    protected static final AttributeDefinition NO_RESOLVE_LOCAL_CACHE = SimpleAttributeDefinitionBuilder.create(InstMgrConstants.NO_RESOLVE_LOCAL_CACHE, ModelType.BOOLEAN)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setDefaultValue(ModelNode.FALSE)
            .setRequired(false)
            .setStorageRuntime()
            .setAlternatives(InstMgrConstants.LOCAL_CACHE)
            .build();

    AbstractInstMgrUpdateHandler(InstMgrService imService, InstallationManagerFactory imf) {
        super(imService, imf);
    }

    protected List<Repository> toRepositories(ModelNode repositoriesMn) {
        final List<Repository> result = new ArrayList<>();

        if (repositoriesMn != null) {
            final List<ModelNode> repositoriesLst = repositoriesMn.asListOrEmpty();
            for (ModelNode repositoryMnEntry : repositoriesLst) {
                ModelNode repositoryMn = repositoryMnEntry.get(REPOSITORY.getName());
                String id = repositoryMn.get(REPOSITORY_ID.getName()).asStringOrNull();
                String url = repositoryMn.get(REPOSITORY_URL.getName()).asStringOrNull();
                result.add(new Repository(id, url));
            }
        }

        return result;
    }

    protected Path getUploadedMvnRepoRoot(Path mavenRepoParentWorkdir) throws Exception {
        try (Stream<Path> content = Files.list(mavenRepoParentWorkdir)){
            // We should have two files here, one the directory and the other the
            List<Path> entries = content.filter( e -> e.toFile().isDirectory()).collect(Collectors.toList());
            if (entries.size() != 1) {
                // @TODO  Make it a logger entry
                throw new Exception("Invalid Zip Content");
            }

            return entries.get(0);
        }
    }
}

package org.wildfly.core.instmgr.cli;

import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.instmgr.InstMgrConstants;
import org.wildfly.core.instmgr.InstMgrListUpdatesHandler;
import org.wildfly.core.instmgr.InstMgrPrepareUpdateHandler;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;

public class PrepareUpdateAction extends AbstractInstMgrCommand {
    private final File mavenRepoFile;
    private final List<String> repositories;
    private final Path localCache;
    private final boolean noResolveLocalCache;

    private final Path listUpdatesWorkDir;

    private final boolean offline;

    public PrepareUpdateAction(Builder builder) {
        this.mavenRepoFile = builder.mavenRepoFile;
        this.repositories = builder.repositories;
        this.localCache = builder.localCache;
        this.noResolveLocalCache = builder.noResolveLocalCache;
        this.listUpdatesWorkDir = builder.listUpdatesWorkDir;
        this.offline = builder.offline;
    }

    @Override
    protected Operation buildOperation() {
        final ModelNode op = new ModelNode();
        final OperationBuilder operationBuilder = OperationBuilder.create(op);

        op.get(OP).set(InstMgrPrepareUpdateHandler.DEFINITION.getName());

        if (mavenRepoFile != null) {
            op.get(InstMgrConstants.MAVEN_REPO_FILE).set(0);
            operationBuilder.addFileAsAttachment(mavenRepoFile);
        }

        if (repositories != null && !repositories.isEmpty()) {
            ModelNode repositoriesMn = new ModelNode().addEmptyList();
            for (String repoStr : repositories) {
                ModelNode repositoryMn = new ModelNode();
                String[] split = repoStr.split("::");
                if (split.length == 0) {
                    split = new String[]{"id0", split[1]};
                }
                repositoryMn.get(InstMgrConstants.ID).set(split[0]);
                repositoryMn.get(InstMgrConstants.REPOSITORY_URL).set(split[1]);
                repositoriesMn.add(repositoryMn);
            }
            op.get(InstMgrConstants.REPOSITORIES).set(repositoriesMn);
        }

        if (localCache != null) {
            op.get(InstMgrConstants.LOCAL_CACHE, localCache.normalize().toAbsolutePath().toString());
        }

        op.get(InstMgrConstants.NO_RESOLVE_LOCAL_CACHE).set(noResolveLocalCache);
        op.get(InstMgrConstants.OFFLINE).set(offline);

        if (listUpdatesWorkDir != null) {
            op.get(InstMgrConstants.LIST_UPDATES_WORK_DIR, listUpdatesWorkDir.normalize().toAbsolutePath().toString());
        }

        // @TODO validate Operation in the client?
        return operationBuilder.build();
    }

    public static class Builder {
        public boolean offline;
        private File mavenRepoFile;
        private List<String> repositories;
        private Path localCache;

        private boolean noResolveLocalCache;
        private Path listUpdatesWorkDir;

        public Builder() {
            this.repositories = new ArrayList<>();
            this.offline = false;
            this.noResolveLocalCache = false;
        }

        public Builder setMavenRepoFile(File mavenRepoFile) {
            if (mavenRepoFile != null) {
                this.mavenRepoFile = mavenRepoFile;
            }
            return this;
        }

        public Builder setRepositories(List<String> repositories) {
            if (repositories != null) {
                this.repositories = new ArrayList<>(repositories);
            }
            return this;
        }

        public Builder setLocalCache(Path localCache) {
            if (localCache != null) {
                this.localCache = localCache;
            }
            return this;
        }

        public Builder setNoResolveLocalCache(boolean noResolveLocalCache) {
            this.noResolveLocalCache = noResolveLocalCache;
            return this;
        }

        public Builder setOffline(boolean offline) {
            this.offline = offline;
            return this;
        }

        public Builder setListUpdatesWorkDir(Path listUpdatesWorkDir) {
            if (listUpdatesWorkDir != null) {
                this.listUpdatesWorkDir = listUpdatesWorkDir;
            }
            return this;
        }

        public PrepareUpdateAction build() {
            return new PrepareUpdateAction(this);
        }
    }
}

package org.wildfly.core.instmgr.cli;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.instmgr.InstMgrConstants;
import org.wildfly.core.instmgr.InstMgrPrepareUpdateHandler;
import org.wildfly.core.instmgr.InstMgrRevertHandler;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;

@CommandDefinition(name = "Reverts to a previous installation state", description = "Apply the latest available patches on a server instance.")
public class RevertCommand extends AbstractInstMgrCommand {

    @Option(name = "host", completer = AbstractInstMgrCommand.HostsCompleter.class, activator = AbstractInstMgrCommand.HostsActivator.class)
    protected String host;

    private final File mavenRepoFile;
    private final List<String> repositories;
    private final Path localCache;
    private final boolean noResolveLocalCache;

    private final boolean offline;

    public RevertCommand(Builder builder) {
        this.mavenRepoFile = builder.mavenRepoFile;
        this.repositories = builder.repositories;
        this.localCache = builder.localCache;
        this.noResolveLocalCache = builder.noResolveLocalCache;
        this.offline = builder.offline;
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        final CommandContext ctx = commandInvocation.getCommandContext();
        final ModelControllerClient client = ctx.getModelControllerClient();
        if (client == null) {
            ctx.print("<connect to the controller and re-run the version command to see the release info>\n");
        } else {
            ModelNode response = this.executeOp(ctx, host);
            printResponse(ctx, response);
        }

        return CommandResult.SUCCESS;
    }

    @Override
    protected Operation buildOperation() {
        final ModelNode op = new ModelNode();
        final OperationBuilder operationBuilder = OperationBuilder.create(op);

        op.get(OP).set(InstMgrRevertHandler.DEFINITION.getName());

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

        // @TODO validate Operation in the client?
        return operationBuilder.build();
    }

    public static class Builder {
        public boolean offline;
        private File mavenRepoFile;
        private List<String> repositories;
        private Path localCache;

        private boolean noResolveLocalCache;

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

        public RevertCommand build() {
            return new RevertCommand(this);
        }
    }
}

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

package org.wildfly.core.instmgr.cli;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.instmgr.InstMgrConstants;
import org.wildfly.core.instmgr.InstMgrPrepareRevertHandler;

import java.io.File;
import java.util.List;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;

@CommandDefinition(name = "revert", description = "Reverts to a previous installation state.")
public class RevertCommand extends AbstractInstMgrCommand {
    @OptionList(name = "repositories")
    private List<String> repositories;
    @Option(name = "local-cache")
    private File localCache;
    @Option(name = "no-resolve-local-cache", hasValue = false)
    private boolean noResolveLocalCache;
    @Option(name = "offline", hasValue = false)
    private boolean offline;
    @Option(name = "maven-repo-file")
    private File mavenRepoFile;
    @Option(name = "revision")
    private String revision;

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        final CommandContext ctx = commandInvocation.getCommandContext();
        final ModelControllerClient client = ctx.getModelControllerClient();
        if (client == null) {
            ctx.printLine("You are disconnected at the moment. Type 'connect' to connect to the server or 'help' for the list of supported commands.");
            return CommandResult.FAILURE;
        }

        ModelNode response = this.executeOp(ctx, this.host);
        printResponse(ctx, response);

        return CommandResult.SUCCESS;
    }

    @Override
    protected Operation buildOperation() throws CommandException {
        final ModelNode op = new ModelNode();
        final OperationBuilder operationBuilder = OperationBuilder.create(op);

        op.get(OP).set(InstMgrPrepareRevertHandler.DEFINITION.getName());

        if (mavenRepoFile != null) {
            op.get(InstMgrConstants.MAVEN_REPO_FILE).set(0);
            operationBuilder.addFileAsAttachment(mavenRepoFile);
        }

        addRepositoriesToModelNode(op, this.repositories);

        if (localCache != null) {
            op.get(InstMgrConstants.LOCAL_CACHE, localCache.toPath().normalize().toAbsolutePath().toString());
        }

        op.get(InstMgrConstants.NO_RESOLVE_LOCAL_CACHE).set(noResolveLocalCache);
        op.get(InstMgrConstants.OFFLINE).set(offline);

        if (revision != null) {
            op.get(InstMgrConstants.REVISION).set(revision);
        }

        // @TODO validate Operation in the client?
        return operationBuilder.build();
    }
}

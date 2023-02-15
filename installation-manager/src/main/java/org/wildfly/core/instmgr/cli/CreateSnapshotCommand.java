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

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.CLIModelControllerClient;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.instmgr.InstMgrCreateSnapshotHandler;

import java.io.File;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;

@CommandDefinition(name = "clone-export", description = "Export the installation metadata.")
public class CreateSnapshotCommand extends AbstractInstMgrCommand {
    @Option(name = "host", completer = AbstractInstMgrCommand.HostsCompleter.class, activator = AbstractInstMgrCommand.HostsActivator.class)
    protected String host;

    @Option(name = "path", required = true)
    private File path;
    @Override
    protected Operation buildOperation() {
        final ModelNode op = new ModelNode();
        op.get(OP).set(InstMgrCreateSnapshotHandler.DEFINITION.getName());

        if (path != null) {
            op.get(ModelDescriptionConstants.PATH).set(path.getAbsolutePath());
        }

        return OperationBuilder.create(op).build();
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        final CommandContext ctx = commandInvocation.getCommandContext();
        final ModelControllerClient client = ctx.getModelControllerClient();
        if (client == null || (client instanceof CLIModelControllerClient && !((CLIModelControllerClient)client).isConnected())) {
            ctx.printLine("You are disconnected at the moment. Type 'connect' to connect to the server or 'help' for the list of supported commands.");
            return CommandResult.FAILURE;
        }

        ModelNode response = this.executeOp(commandInvocation.getCommandContext(), host);
        ModelNode result = response.get(Util.RESULT);
        ctx.printLine(result.asString());
        return CommandResult.SUCCESS;
    }
}

package org.wildfly.core.instmgr.cli;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
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
public class CreateSnapshotCommand extends AbstractInstMgrCommand implements Command<CLICommandInvocation> {
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
        if (client == null) {
            ctx.print("<connect to the controller and re-run the version command to see the release info>\n");
            return CommandResult.FAILURE;
        }

        ModelNode modelNode = this.executeOp(commandInvocation.getCommandContext(), host);
        printResponse(ctx, modelNode);
        return CommandResult.SUCCESS;
    }
}

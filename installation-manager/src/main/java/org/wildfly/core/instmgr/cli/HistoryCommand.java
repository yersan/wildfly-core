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
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.instmgr.InstMgrConstants;
import org.wildfly.core.instmgr.InstMgrHistoryHandler;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;

@CommandDefinition(name = "history", description = "List previous installation states.")
public class HistoryCommand extends AbstractInstMgrCommand implements Command<CLICommandInvocation> {

    @Option(name = "host", completer = AbstractInstMgrCommand.HostsCompleter.class, activator = AbstractInstMgrCommand.HostsActivator.class)
    protected String host;

    @Option(name = "revision")
    private String revision;

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        final CommandContext ctx = commandInvocation.getCommandContext();
        final ModelControllerClient client = ctx.getModelControllerClient();
        if (client == null) {
            ctx.print("<connect to the controller and re-run the version command to see the release info>\n");
            return CommandResult.FAILURE;
        }

        ModelNode response = this.executeOp(ctx, host);
        printResponse(ctx, response);
        return CommandResult.SUCCESS;
    }

    @Override
    protected Operation buildOperation() {
        final ModelNode op = new ModelNode();
        op.get(OP).set(InstMgrHistoryHandler.DEFINITION.getName());
        if (revision != null) {
            op.get(InstMgrConstants.REVISION).set(revision);
        }

        return OperationBuilder.create(op).build();
    }
}

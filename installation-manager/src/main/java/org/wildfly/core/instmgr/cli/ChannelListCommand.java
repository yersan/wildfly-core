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
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.instmgr.InstMgrConstants;

import java.util.List;

@CommandDefinition(name = "channel-list", description = "List channels subscribed to by the installation.")
public class ChannelListCommand extends AbstractInstMgrCommand {
    @Option(name = "host", completer = AbstractInstMgrCommand.HostsCompleter.class, activator = AbstractInstMgrCommand.HostsActivator.class)
    protected String host;

    @Override
    protected Operation buildOperation() {
        final ModelNode op = new ModelNode();
        op.get(Util.OPERATION).set(Util.READ_RESOURCE);
        op.get(Util.INCLUDE_RUNTIME).set(true);

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
        List<ModelNode> channelsMn = result.get(InstMgrConstants.CHANNELS).asListOrEmpty();

        ctx.printLine("-------");
        for (ModelNode channel : channelsMn) {
            ctx.printLine("#" + channel.get(InstMgrConstants.CHANNEL_NAME));
            String manifest;
            if (channel.get(InstMgrConstants.MANIFEST).isDefined()) {
                ModelNode manifestMn = channel.get(InstMgrConstants.MANIFEST);
                if (manifestMn.get(InstMgrConstants.MANIFEST_GAV).isDefined()) {
                    manifest = manifestMn.get(InstMgrConstants.MANIFEST_GAV).asString();
                } else {
                    manifest = manifestMn.get(InstMgrConstants.MANIFEST_URL).asString();
                }
                ctx.printLine("  " + "manifest: " + manifest);
            }

            if (channel.get(InstMgrConstants.REPOSITORIES).isDefined()) {
                ctx.printLine("  " + "repositories:");
                List<ModelNode> repositoriesMn = channel.get(InstMgrConstants.REPOSITORIES).asListOrEmpty();
                for (ModelNode repository : repositoriesMn) {
                    ctx.printLine("  " + "  " + "id: " + repository.get(InstMgrConstants.ID));
                    ctx.printLine("  " + "  " + "url: " + repository.get(InstMgrConstants.REPOSITORY_URL));
                }
            }
            ctx.printLine("-------");
        }
        return CommandResult.SUCCESS;
    }
}

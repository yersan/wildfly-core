package org.wildfly.core.instmgr.cli;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.instmgr.InstMgrCleanHandler;
import org.wildfly.core.instmgr.InstMgrConstants;

import java.nio.file.Path;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;

@CommandDefinition(name = "clean", description = "Clean installation manager content")
public class CleanCommand extends AbstractInstMgrCommand implements Command<CLICommandInvocation> {
    @Option(name = "host", completer = AbstractInstMgrCommand.HostsCompleter.class, activator = AbstractInstMgrCommand.HostsActivator.class)
    protected String host;

    final Path lstUpdatesWorkDir;

    public CleanCommand() {
        this.lstUpdatesWorkDir = null;
    }
    public CleanCommand(Path lstUpdatesWorkDir) {
        this.lstUpdatesWorkDir = lstUpdatesWorkDir;
    }

    @Override
    protected Operation buildOperation() {
        final ModelNode op = new ModelNode();
        op.get(OP).set(InstMgrCleanHandler.DEFINITION.getName());
        if (lstUpdatesWorkDir != null) {
            op.get(InstMgrConstants.LIST_UPDATES_WORK_DIR).set(lstUpdatesWorkDir.toString());
        }

        return OperationBuilder.create(op).build();
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        this.executeOp(commandInvocation.getCommandContext(), host);
        return CommandResult.SUCCESS;
    }
}

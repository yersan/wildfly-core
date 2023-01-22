package org.wildfly.core.instmgr.cli;

import org.aesh.command.Command;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.GroupCommandDefinition;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

@GroupCommandDefinition(name = InstMgrCommand.COMMAND_NAME, description = "", groupCommands = {
        UpdateCommand.class,
        CleanCommand.class,
        RevertCommand.class
})
public class InstMgrCommand implements Command<CLICommandInvocation> {
    public static final String COMMAND_NAME = "installer";

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        throw new CommandException("Command action is missing.");
    }
}

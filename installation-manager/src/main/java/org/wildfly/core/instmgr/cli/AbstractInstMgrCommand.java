package org.wildfly.core.instmgr.cli;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.completer.OptionCompleter;
import org.aesh.command.impl.internal.ParsedCommand;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.CLICompleterInvocation;
import org.wildfly.core.cli.command.aesh.activator.AbstractOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.DomainOptionActivator;
import org.wildfly.core.instmgr.InstMgrConstants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.jboss.as.cli.Util.ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;

@CommandDefinition(name = "abstract-inst-mgr-cmd", description = "")
public abstract class AbstractInstMgrCommand implements Command<CLICommandInvocation> {
    static final PathElement CORE_SERVICE_INSTALLER = PathElement.pathElement(CORE_SERVICE, InstMgrCommand.COMMAND_NAME);

    protected ModelNode executeOp(CommandContext ctx, String host) throws CommandException {
        if (host != null && !ctx.isDomainMode()) {
            throw new CommandException("The --host option is not available in the current context. "
                    + "Connection to the controller might be unavailable or not running in domain mode.");
        } else if (host == null && ctx.isDomainMode()) {
            throw new CommandException("The --host option must be used in domain mode.");
        }

        PathAddress address;
        if (ctx.isDomainMode()) {
            address = createHost(host, ctx.getModelControllerClient());
        } else {
            address = createStandalone();
        }

        final Operation request = buildOperation();
        request.getOperation().get(ADDRESS).set(address.toModelNode());

        ModelNode response;
        try {
            response = ctx.getModelControllerClient().execute(request);
        } catch (IOException ex) {
            throw new CommandException(ex);
        }

        if (!Util.isSuccess(response)) {
            throw new CommandException(Util.getFailureDescription(response));
        }

        return response;
    }

    protected abstract Operation buildOperation();

    public static class HostsActivator extends AbstractOptionActivator implements DomainOptionActivator {

        @Override
        public boolean isActivated(ParsedCommand processedCommand) {
            return getCommandContext().getModelControllerClient() != null && getCommandContext().isDomainMode();
        }
    }

    public static class HostsCompleter implements OptionCompleter<CLICompleterInvocation> {

        @Override
        public void complete(CLICompleterInvocation completerInvocation) {
            List<String> values = new ArrayList<>();
            Collection<String> candidates = getCandidates(completerInvocation.getCommandContext());
            String opBuffer = completerInvocation.getGivenCompleteValue();
            if (opBuffer.isEmpty()) {
                values.addAll(candidates);
            } else {
                for (String name : candidates) {
                    if (name.startsWith(opBuffer)) {
                        values.add(name);
                    }
                }
                Collections.sort(values);
            }
            completerInvocation.addAllCompleterValues(values);
        }

        private Collection<String> getCandidates(CommandContext ctx) {
            return CandidatesProviders.HOSTS.getAllCandidates(ctx);
        }
    }

    private PathAddress createStandalone() {
        return PathAddress.pathAddress(CORE_SERVICE_INSTALLER);
    }

    public static final PathAddress createHost(final String hostName, final ModelControllerClient client) {
        final PathElement host = PathElement.pathElement(HOST, hostName);
        final PathAddress address = PathAddress.pathAddress(host, CORE_SERVICE_INSTALLER);

        return address;
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        return CommandResult.SUCCESS;
    }

    protected void printResponse(CommandContext ctx, ModelNode response) {
        ModelNode clone = response.clone();
        if (Util.isSuccess(clone)) {
            if (clone.hasDefined(Util.RESULT)) {
                ModelNode clonedResult = clone.get(Util.RESULT);
                if (clonedResult.hasDefined(InstMgrConstants.RETURN_CODE)) {
                    clonedResult.remove(InstMgrConstants.RETURN_CODE);
                }
                if (clonedResult.hasDefined(InstMgrConstants.LIST_UPDATES_WORK_DIR)) {
                    clonedResult.remove(InstMgrConstants.LIST_UPDATES_WORK_DIR);
                }
            }
        }

        ctx.printDMR(clone);
    }
}

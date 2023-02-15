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
import org.aesh.command.completer.OptionCompleter;
import org.aesh.command.impl.internal.ParsedCommand;
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
import org.wildfly.core.cli.command.aesh.activator.AbstractRejectOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.DomainOptionActivator;
import org.wildfly.core.instmgr.InstMgrConstants;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.jboss.as.cli.Util.ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.wildfly.core.instmgr.cli.UpdateCommand.CONFIRM_OPTION;
import static org.wildfly.core.instmgr.cli.UpdateCommand.DRY_RUN_OPTION;

@CommandDefinition(name = "abstract-inst-mgr-cmd", description = "")
public abstract class AbstractInstMgrCommand implements Command<CLICommandInvocation> {
    static final PathElement CORE_SERVICE_INSTALLER = PathElement.pathElement(CORE_SERVICE, InstMgrGroupCommand.COMMAND_NAME);

    /**
     * General Execute Operation method.
     *
     * @param ctx
     * @param host
     * @return ModelNode with the result of a successful execution.
     * @throws CommandException If the operation was not success or an error occurred.
     */
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
            throw new CommandException("Failed to execute the Operation " + request.getOperation().asString(), ex);
        }

        if (!Util.isSuccess(response)) {
            throw new CommandException(Util.getFailureDescription(response));
        }

        return response;
    }

    protected abstract Operation buildOperation();

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        throw new CommandException("Command action is missing.");
    }

    public static class HostsActivator extends AbstractOptionActivator implements DomainOptionActivator {

        @Override
        public boolean isActivated(ParsedCommand processedCommand) {
            return getCommandContext().getModelControllerClient() != null && getCommandContext().isDomainMode();
        }
    }

    public static class DryRunActivator extends AbstractRejectOptionActivator {
        public DryRunActivator() {
            super(CONFIRM_OPTION);
        }
    }

    public static class ConfirmActivator extends AbstractRejectOptionActivator {
        public ConfirmActivator() {
            super(DRY_RUN_OPTION);
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

    private static PathAddress createStandalone() {
        return PathAddress.pathAddress(CORE_SERVICE_INSTALLER);
    }

    private static PathAddress createHost(final String hostName, final ModelControllerClient client) {
        final PathElement host = PathElement.pathElement(HOST, hostName);
        final PathAddress address = PathAddress.pathAddress(host, CORE_SERVICE_INSTALLER);

        return address;
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

    protected void addRepositories(ModelNode op, List<String> repositories) {
        if (repositories != null && !repositories.isEmpty()) {
            ModelNode repositoriesMn = new ModelNode().addEmptyList();
            for (String repoStr : repositories) {
                ModelNode repositoryMn = new ModelNode();
                String idStr;
                String urlStr;
                String[] split = repoStr.split("::");
                try {
                    if (split.length == 1) {
                        new URL(repoStr).toURI();
                        idStr = "id0";
                        urlStr = repoStr;
                    } else if (split.length == 2) {
                        idStr = split[0];
                        urlStr = split[1];
                    } else {
                        throw new IllegalArgumentException();
                    }
                    repositoryMn.get(InstMgrConstants.ID).set(idStr);
                    repositoryMn.get(InstMgrConstants.REPOSITORY_URL).set(urlStr);
                    repositoriesMn.add(repositoryMn);
                } catch (Exception w) {
                    throw new IllegalArgumentException("Invalid Repository URL. Valid values are either URLs or ID::URL");
                }
            }
            op.get(InstMgrConstants.REPOSITORIES).set(repositoriesMn);
        }
    }
}

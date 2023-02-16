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
import org.aesh.readline.Prompt;
import org.aesh.terminal.utils.Config;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.CLIModelControllerClient;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.instmgr.InstMgrConstants;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@CommandDefinition(name = "update", description = "Apply the latest available patches on a server instance.")
public class UpdateCommand extends AbstractInstMgrCommand {
    static final String DRY_RUN_OPTION = "dry-run";
    static final String CONFIRM_OPTION = "confirm";
    @Option(name = DRY_RUN_OPTION, hasValue = false, activator = AbstractInstMgrCommand.DryRunActivator.class)
    private boolean dryRun;
    @Option(name = CONFIRM_OPTION, hasValue = false, activator = AbstractInstMgrCommand.ConfirmActivator.class)
    private boolean confirm;
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

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        final CommandContext ctx = commandInvocation.getCommandContext();
        final ModelControllerClient client = ctx.getModelControllerClient();
        if (client == null) {
            ctx.printLine("You are disconnected at the moment. Type 'connect' to connect to the server or 'help' for the list of supported commands.");
            return CommandResult.FAILURE;
        }

        if (confirm && dryRun) {
            ctx.printLine(CONFIRM_OPTION + " and " + DRY_RUN_OPTION + " cannot be used at the same time.");
            return CommandResult.FAILURE;
        }

        ListUpdatesAction.Builder listUpdatesCmdBuilder = new ListUpdatesAction.Builder()
                .setNoResolveLocalCache(noResolveLocalCache)
                .setLocalCache(localCache)
                .setRepositories(repositories)
                .setMavenRepoFile(mavenRepoFile)
                .setOffline(offline);

        ListUpdatesAction listUpdatesCmd = listUpdatesCmdBuilder.build();
        ModelNode response = listUpdatesCmd.executeOp(ctx, this.host);

        if (response.hasDefined(Util.RESULT)) {
            ModelNode result = response.get(Util.RESULT);
            printUpdatesResult(ctx, result.get(InstMgrConstants.UPDATES_RESULT));

            if (dryRun) {
                return CommandResult.SUCCESS;
            }

            int returnCode = result.get(InstMgrConstants.RETURN_CODE).asInt();
            Path lstUpdatesWorkDir = null;
            if (returnCode == InstMgrConstants.RETURN_CODE_UPDATES_WITH_WORK_DIR) {
                lstUpdatesWorkDir = Paths.get(result.get(InstMgrConstants.LIST_UPDATES_WORK_DIR).asString());
            }

            if (returnCode != InstMgrConstants.RETURN_CODE_NO_UPDATES) {
                if (!confirm) {
                    String reply = null;

                    try {
                        while (reply == null) {
                            reply = commandInvocation.inputLine(new Prompt("Would you like to proceed with preparing this update? [y/N]:"));
                            if (reply != null && reply.equalsIgnoreCase("N")) {
                                // clean the cache if there is one
                                if (returnCode == InstMgrConstants.RETURN_CODE_UPDATES_WITH_WORK_DIR) {
                                    CleanCommand cleanCommand = new CleanCommand.Builder().setLstUpdatesWorkDir(lstUpdatesWorkDir).createCleanCommand();
                                    cleanCommand.executeOp(ctx, this.host);
                                }

                                return CommandResult.SUCCESS;
                            } else if (reply != null && reply.equalsIgnoreCase("y")) {
                                break;
                            }
                        }
                    } catch (InterruptedException e) {
                        // In case of an error, clean the cache if there is one
                        if (returnCode == InstMgrConstants.RETURN_CODE_UPDATES_WITH_WORK_DIR) {
                            CleanCommand cleanCommand = new CleanCommand.Builder().setLstUpdatesWorkDir(lstUpdatesWorkDir).createCleanCommand();
                            cleanCommand.executeOp(ctx, this.host);
                        }

                        return CommandResult.FAILURE;
                    }
                }

                // trigger an prepare-update
                PrepareUpdateAction.Builder prepareUpdateActionBuilder = new PrepareUpdateAction.Builder()
                        .setNoResolveLocalCache(noResolveLocalCache)
                        .setLocalCache(localCache)
                        .setRepositories(repositories)
                        .setMavenRepoFile(mavenRepoFile)
                        .setOffline(offline)
                        .setListUpdatesWorkDir(lstUpdatesWorkDir);

                PrepareUpdateAction prepareUpdateAction = prepareUpdateActionBuilder.build();
                ModelNode prepareUpdateResult = prepareUpdateAction.executeOp(ctx, this.host);
                printUpdatesResult(ctx, prepareUpdateResult.get(Util.RESULT));
            }
        } else {
            ctx.printLine("Operation result is not available.");
        }

        return CommandResult.SUCCESS;
    }

    private void printUpdatesResult(CommandContext ctx, ModelNode result) {
        final StringBuilder builder = new StringBuilder();

        if (result.getType() == ModelType.LIST) {
            List<ModelNode> modelNodes = result.asList();
            for (ModelNode value : modelNodes) {
                builder.append(value.asString()).append(Config.getLineSeparator());
            }
        } else if (result.getType() == ModelType.STRING) {
            builder.append(result.asString()).append(Config.getLineSeparator());
        }
        ctx.printLine(builder.toString());
    }

    @Override
    protected Operation buildOperation() {
        throw new IllegalStateException("Update Command has not build operation");
    }
}
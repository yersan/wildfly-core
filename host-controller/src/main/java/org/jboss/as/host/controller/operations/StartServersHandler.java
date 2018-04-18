/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.jboss.as.host.controller.operations;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTO_START;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.host.controller.logging.HostControllerLogger.ROOT_LOGGER;

import java.util.Map;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.host.controller.HostControllerEnvironment;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.as.host.controller.HostRunningModeControl;
import org.jboss.as.host.controller.RestartMode;
import org.jboss.as.host.controller.ServerInventory;
import org.jboss.as.host.controller.resources.ServerConfigResourceDefinition;
import org.jboss.as.process.ProcessInfo;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Starts or reconnect all auto-start servers (at boot).
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class StartServersHandler implements OperationStepHandler {

    public static final boolean START_BLOCKING = Boolean.parseBoolean(WildFlySecurityManager.getPropertyPrivileged("org.jboss.as.host.start.servers.sequential", "false"));
    public static final String OPERATION_NAME = "start-servers";

  //Private method does not need resources for description
    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, null)
        .setPrivateEntry()
        .build();

    private final ServerInventory serverInventory;
    private final HostControllerEnvironment hostControllerEnvironment;
    private final HostRunningModeControl runningModeControl;

    /**
     * Create the StartServersHandler
     */
    public StartServersHandler(final HostControllerEnvironment hostControllerEnvironment, final ServerInventory serverInventory, HostRunningModeControl runningModeControl) {
        this.hostControllerEnvironment = hostControllerEnvironment;
        this.serverInventory = serverInventory;
        this.runningModeControl = runningModeControl;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        if (!context.isBooting()) {
            throw new OperationFailedException(HostControllerLogger.ROOT_LOGGER.invocationNotAllowedAfterBoot(operation.require(OP)));
        }

        if (context.getRunningMode() == RunningMode.ADMIN_ONLY) {
            throw new OperationFailedException(HostControllerLogger.ROOT_LOGGER.cannotStartServersInvalidMode(context.getRunningMode()));
        }

        context.acquireControllerLock();
        final ModelNode domainModel = Resource.Tools.readModel(context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS, true));
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                // start servers
                final Resource resource =  context.readResource(PathAddress.EMPTY_ADDRESS);
                final ModelNode hostModel = Resource.Tools.readModel(resource);
                if(hostModel.hasDefined(SERVER_CONFIG)) {

                    final ModelNode servers = hostModel.get(SERVER_CONFIG).clone();
                    final boolean restartRequired = runningModeControl.getRestartMode() == RestartMode.SERVERS;
                    final boolean blockUntilStopped = hostControllerEnvironment.isRestart() || !runningModeControl.isReloadByHandler() || (runningModeControl.isReloadByHandler() && runningModeControl.isBlockUntilStopped());

                    if (blockUntilStopped){
                        reconnectAndWaitStoppingServers(servers, domainModel, true);
                    }

                    if (hostControllerEnvironment.isRestart() || runningModeControl.getRestartMode() == RestartMode.HC_ONLY){
                        restartedHcStartOrReconnectServers(servers, domainModel, context, restartRequired);
                    } else {
                        cleanStartServers(servers, domainModel, context, restartRequired);
                    }
                }
                //Reset runningModeControl values to default for the values that cannot be reset at ProcessReloadHandler.ReloadContext.doReloado
                runningModeControl.setRestartMode(RestartMode.SERVERS);
                runningModeControl.setBlockUntilStopped(true);
                context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
            }
        }, OperationContext.Stage.RUNTIME);
    }

    private void reconnectAndWaitStoppingServers(final ModelNode servers, final ModelNode domainModel, boolean blockUntilStopped){
        Map<String, ProcessInfo> processInfos = serverInventory.determineRunningProcesses();
        for(final String serverName : servers.keys()) {
            ProcessInfo info = processInfos.get(serverInventory.getServerProcessName(serverName));
            if (info != null){
                serverInventory.reconnectServer(serverName, domainModel, info.getAuthKey(), info.isRunning(), info.isStopping(), blockUntilStopped, false, false);
            }
        }
    }

    private void cleanStartServers(final ModelNode servers, final ModelNode domainModel, OperationContext context, boolean restartRequired) throws OperationFailedException {
        Map<String, ProcessInfo> processInfos = serverInventory.determineRunningProcesses();
        for(final Property serverProp : servers.asPropertyList()) {
            String serverName = serverProp.getName();
            if (ServerConfigResourceDefinition.AUTO_START.resolveModelAttribute(context, serverProp.getValue()).asBoolean(true)) {
                ProcessInfo info = processInfos.get(serverInventory.getServerProcessName(serverName));
                if ( info != null ){
                    serverInventory.reconnectServer(serverName, domainModel, info.getAuthKey(), info.isRunning(), info.isStopping(), false, restartRequired, START_BLOCKING);
                } else {
                    try {
                        serverInventory.startServer(serverName, domainModel, START_BLOCKING, false);
                    } catch (Exception e) {
                        ROOT_LOGGER.failedToStartServer(e, serverName);
                    }
                }
            }
        }
    }

    private void restartedHcStartOrReconnectServers(final ModelNode servers, final ModelNode domainModel, final OperationContext context, boolean restartRequired){
        Map<String, ProcessInfo> processInfos = serverInventory.determineRunningProcesses();
        for(final String serverName : servers.keys()) {
            ProcessInfo info = processInfos.get(serverInventory.getServerProcessName(serverName));
            boolean auto = servers.get(serverName, AUTO_START).asBoolean(true);
            if (info == null && auto) {
                try {
                    serverInventory.startServer(serverName, domainModel, START_BLOCKING, false);
                } catch (Exception e) {
                    ROOT_LOGGER.failedToStartServer(e, serverName);
                }
            } else if (info != null){
                // Reconnect the server using the current authKey
                serverInventory.reconnectServer(serverName, domainModel, info.getAuthKey(), info.isRunning(), info.isStopping(), false, restartRequired && auto, START_BLOCKING);
            }
        }
    }
}

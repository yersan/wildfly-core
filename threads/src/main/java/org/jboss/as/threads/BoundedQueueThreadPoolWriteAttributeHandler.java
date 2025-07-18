/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.threads;


import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;


/**
 * Handles attribute writes for a bounded queue thread pool.
 * @author Alexey Loubyansky
 */
public class BoundedQueueThreadPoolWriteAttributeHandler extends ThreadsWriteAttributeOperationHandler {

    private final ServiceName serviceNameBase;
    private final RuntimeCapability<Void> capability;

    public BoundedQueueThreadPoolWriteAttributeHandler(boolean blocking, final RuntimeCapability<Void> capability, ServiceName serviceNameBase) {
        super(blocking ? BoundedQueueThreadPoolAdd.BLOCKING_ATTRIBUTES : BoundedQueueThreadPoolAdd.NON_BLOCKING_ATTRIBUTES);
        this.serviceNameBase = serviceNameBase;
        this.capability = capability;
    }

    @Override
    protected void applyOperation(final OperationContext context, ModelNode model, String attributeName,
                                  ServiceController<?> service, boolean forRollback) throws OperationFailedException {

        final EnhancedQueueExecutorService pool =  (EnhancedQueueExecutorService) service.getService();

        if (PoolAttributeDefinitions.KEEPALIVE_TIME.getName().equals(attributeName)) {
            TimeUnit defaultUnit = pool.getKeepAliveUnit();
            final TimeSpec spec = getTimeSpec(context, model, defaultUnit);
            pool.setKeepAlive(spec);
        } else if(PoolAttributeDefinitions.MAX_THREADS.getName().equals(attributeName)) {
            pool.setMaxThreads(PoolAttributeDefinitions.MAX_THREADS.resolveModelAttribute(context, model).asInt());
        } else if(PoolAttributeDefinitions.CORE_THREADS.getName().equals(attributeName)) {
            int coreCount;
            ModelNode coreNode = PoolAttributeDefinitions.CORE_THREADS.resolveModelAttribute(context, model);
            if (coreNode.isDefined()) {
                coreCount = coreNode.asInt();
            } else {
                // Core is same as max
                coreCount = PoolAttributeDefinitions.MAX_THREADS.resolveModelAttribute(context, model).asInt();
            }
            pool.setCoreThreads(coreCount);
        } else if(PoolAttributeDefinitions.QUEUE_LENGTH.getName().equals(attributeName)) {
            if (forRollback) {
                context.revertReloadRequired();
            } else {
                context.reloadRequired();
            }
        } else if (PoolAttributeDefinitions.ALLOW_CORE_TIMEOUT.getName().equals(attributeName)) {
            pool.setAllowCoreTimeout(PoolAttributeDefinitions.ALLOW_CORE_TIMEOUT.resolveModelAttribute(context, model).asBoolean());
        } else if (!forRollback) {
            // Programming bug. Throw a RuntimeException, not OFE, as this is not a client error
            throw ThreadsLogger.ROOT_LOGGER.unsupportedBoundedQueueThreadPoolAttribute(attributeName);
        }
    }

    @Override
    protected ServiceController<?> getService(final OperationContext context, final ModelNode model) throws OperationFailedException {
        final String name = context.getCurrentAddressValue();
        ServiceName serviceName = null;
        ServiceController<?> controller = null;
        if(capability != null) {
            serviceName = capability.getCapabilityServiceName(context.getCurrentAddress());
            controller = context.getServiceRegistry(true).getService(serviceName);
            if(controller != null) {
                return controller;
            }
        }
        if (serviceNameBase != null) {
            serviceName = serviceNameBase.append(name);
            controller = context.getServiceRegistry(true).getService(serviceName);
        }
        if(controller == null) {
            throw ThreadsLogger.ROOT_LOGGER.boundedQueueThreadPoolServiceNotFound(serviceName);
        }
        return controller;
    }
}

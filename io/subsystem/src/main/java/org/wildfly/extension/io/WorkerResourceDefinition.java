/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.LongRangeValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.DelegatingResource;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.PlaceholderResource;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.registry.ResourceProvider;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.wildfly.extension.io.logging.IOLogger;
import org.wildfly.io.IOServiceDescriptor;
import org.wildfly.io.OptionAttributeDefinition;
import org.xnio.Option;
import org.xnio.Options;
import org.xnio.XnioWorker;
import org.xnio.management.XnioServerMXBean;
import org.xnio.management.XnioWorkerMXBean;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2012 Red Hat Inc.
 */
class WorkerResourceDefinition extends PersistentResourceDefinition {

    static final PathElement PATH = PathElement.pathElement(Constants.WORKER);
    static final RuntimeCapability<Void> CAPABILITY = RuntimeCapability.Builder.of(IOServiceDescriptor.WORKER).build();

    static final OptionAttributeDefinition WORKER_TASK_CORE_THREADS = new OptionAttributeDefinition.Builder(Constants.WORKER_TASK_CORE_THREADS, Options.WORKER_TASK_CORE_THREADS)
            .setDefaultValue(new ModelNode(2))
            .setValidator(IntRangeValidator.NON_NEGATIVE)
            .setAllowExpression(true)
            .build();

    static final OptionAttributeDefinition WORKER_TASK_MAX_THREADS = new OptionAttributeDefinition.Builder(Constants.WORKER_TASK_MAX_THREADS, Options.WORKER_TASK_MAX_THREADS)
            .setValidator(IntRangeValidator.NON_NEGATIVE)
            .setAllowExpression(true)
            .build();
    static final OptionAttributeDefinition WORKER_TASK_KEEPALIVE = new OptionAttributeDefinition.Builder(Constants.WORKER_TASK_KEEPALIVE, Options.WORKER_TASK_KEEPALIVE)
            .setDefaultValue(new ModelNode(60_000))
            .setValidator(IntRangeValidator.NON_NEGATIVE)
            .setAllowExpression(true)
            .build();
    static final OptionAttributeDefinition STACK_SIZE = new OptionAttributeDefinition.Builder(Constants.STACK_SIZE, Options.STACK_SIZE)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .setDefaultValue(ModelNode.ZERO_LONG)
            .setValidator(LongRangeValidator.NON_NEGATIVE)
            .setAllowExpression(true)
            .build();
    static final OptionAttributeDefinition WORKER_IO_THREADS = new OptionAttributeDefinition.Builder(Constants.WORKER_IO_THREADS, Options.WORKER_IO_THREADS)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .setValidator(IntRangeValidator.NON_NEGATIVE)
            .setAllowExpression(true)
            .build();

    static final OptionAttributeDefinition[] ATTRIBUTES = new OptionAttributeDefinition[] {
            WORKER_IO_THREADS,
            WORKER_TASK_CORE_THREADS,
            WORKER_TASK_KEEPALIVE,
            WORKER_TASK_MAX_THREADS,
            STACK_SIZE
    };

    private static final AttributeDefinition SHUTDOWN_REQUESTED = new SimpleAttributeDefinitionBuilder("shutdown-requested", ModelType.BOOLEAN).setStorageRuntime().build();
    private static final AttributeDefinition CORE_WORKER_POOL_SIZE = new SimpleAttributeDefinitionBuilder("core-pool-size", ModelType.INT).build();
    private static final AttributeDefinition MAX_WORKER_POOL_SIZE = new SimpleAttributeDefinitionBuilder("max-pool-size", ModelType.INT).build();
    private static final AttributeDefinition IO_THREAD_COUNT = new SimpleAttributeDefinitionBuilder("io-thread-count", ModelType.INT).build();
    private static final AttributeDefinition QUEUE_SIZE = new SimpleAttributeDefinitionBuilder("queue-size", ModelType.INT).build();
    private static final AttributeDefinition BUSY_WORKER_THREAD_COUNT = new SimpleAttributeDefinitionBuilder("busy-task-thread-count", ModelType.INT).build();

    WorkerResourceDefinition(AtomicInteger maxThreads) {
        super(new SimpleResourceDefinition.Parameters(PATH, IOSubsystemResourceDefinitionRegistrar.RESOLVER.createChildResolver(PATH))
                .setAddHandler(new WorkerAdd(maxThreads))
                .setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE)
                .addCapabilities(CAPABILITY));
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return List.of(ATTRIBUTES);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadWriteAttribute(WORKER_TASK_MAX_THREADS,
                new WorkerReadAttributeHandler(WORKER_TASK_MAX_THREADS.getOption()),
                new WorkerWriteAttributeHandler(){
                    @Override
                    boolean setValue(XnioWorker worker, ModelNode value) throws IOException {
                        return worker.setOption(Options.WORKER_TASK_MAX_THREADS, value.asInt()) == null;
                    }
                });
        resourceRegistration.registerReadWriteAttribute(WORKER_TASK_CORE_THREADS,
                new WorkerReadAttributeHandler(WORKER_TASK_CORE_THREADS.getOption()),
                new WorkerWriteAttributeHandler() {
                    @Override
                    boolean setValue(XnioWorker worker, ModelNode value) throws IOException {
                        return worker.setOption(Options.WORKER_TASK_CORE_THREADS, value.asInt()) == null;
                    }
                });
        resourceRegistration.registerReadWriteAttribute(WORKER_TASK_KEEPALIVE,
                new WorkerReadAttributeHandler(WORKER_TASK_KEEPALIVE.getOption()),
                new WorkerWriteAttributeHandler(){
                    @Override
                    boolean setValue(XnioWorker worker, ModelNode value) throws IOException {
                        return worker.setOption(Options.WORKER_TASK_KEEPALIVE, value.asInt()) == null;
                    }
                });
        resourceRegistration.registerReadWriteAttribute(STACK_SIZE,
                new WorkerReadAttributeHandler(STACK_SIZE.getOption()),
                new WorkerWriteAttributeHandler(){
                    @Override
                    boolean setValue(XnioWorker worker, ModelNode value) throws IOException {
                        return worker.setOption(Options.STACK_SIZE, value.asLong()) == null;
                    }
                });
        resourceRegistration.registerReadWriteAttribute(WORKER_IO_THREADS,
                new WorkerReadAttributeHandler(WORKER_IO_THREADS.getOption()),
                new WorkerWriteAttributeHandler(){
                    @Override
                    boolean setValue(XnioWorker worker, ModelNode value) throws IOException {
                        return worker.setOption(Options.WORKER_IO_THREADS, value.asInt()) == null;
                    }
                });

        WorkerMetricsHandler metricsHandler = new WorkerMetricsHandler();
        resourceRegistration.registerReadOnlyAttribute(SHUTDOWN_REQUESTED, metricsHandler);

        resourceRegistration.registerMetric(CORE_WORKER_POOL_SIZE, metricsHandler);
        resourceRegistration.registerMetric(MAX_WORKER_POOL_SIZE, metricsHandler);
        resourceRegistration.registerMetric(IO_THREAD_COUNT, metricsHandler);
        resourceRegistration.registerMetric(QUEUE_SIZE, metricsHandler);
        resourceRegistration.registerMetric(BUSY_WORKER_THREAD_COUNT, metricsHandler);
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        super.registerChildren(resourceRegistration);
        resourceRegistration.registerSubModel(OutboundBindAddressResourceDefinition.getInstance());
        // Don't register on a domain profile, as there are no services to back the resource
        // We could check if (resourceRegistration.getProcessType().isServer()) instead but
        // if we ever support this extension as an HC subsystem (which seems reasonably possible)
        // by doing it this way it will still behave correctly.
        if (!PROFILE.equals(resourceRegistration.getPathAddress().getElement(0).getKey())) {
            resourceRegistration.registerSubModel(new WorkerServerDefinition());
        }
    }

    private abstract static class AbstractWorkerAttributeHandler implements OperationStepHandler {
        private static void populateValueFromModel(OperationContext context, ModelNode operation) {
            final ModelNode subModel = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
            ModelNode result = subModel.get(operation.require(ModelDescriptionConstants.NAME).asString());
            context.getResult().set(result);
        }

        public void execute(OperationContext outContext, ModelNode operation) throws OperationFailedException {
            populateValueFromModel(outContext, operation);
            if (!PROFILE.equals(outContext.getCurrentAddress().getElement(0).getKey())) {
                outContext.addStep((context, op) -> {
                    XnioWorker worker = getXnioWorker(context);
                    if (worker != null) {
                        executeWithWorker(context, op, worker);
                    }
                }, OperationContext.Stage.RUNTIME);
            }
        }

        abstract void executeWithWorker(OperationContext context, ModelNode operation, XnioWorker worker) throws OperationFailedException;
    }

    private abstract static class WorkerWriteAttributeHandler extends AbstractWriteAttributeHandler {

        @Override
        protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode value, ModelNode currentValue, HandbackHolder handbackHolder) throws OperationFailedException {
            XnioWorker worker = getXnioWorker(context);
            if (worker == null || !value.isDefined()) { //worker can be null if it is not started yet, it can happen when there are no dependencies to it.
                return true;
            }
            try {
                return setValue(worker, value);
            } catch (IOException e) {
                throw new OperationFailedException(e);
            }
        }

        @Override
        protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Object handback) throws OperationFailedException {
            XnioWorker worker = getXnioWorker(context);
            if (worker == null) { //worker can be null if it is not started yet, it can happen when there are no dependencies to it.
                return;
            }
            try {
                setValue(worker, valueToRestore);
            } catch (IOException e) {
                throw new OperationFailedException(e);
            }
        }

        /**
         *
         * @return returns true if it requires reload
         */
        abstract boolean setValue(XnioWorker worker, ModelNode value) throws IOException;

    }

    private static class WorkerReadAttributeHandler extends AbstractWorkerAttributeHandler {
        final Option<?> option;

        public WorkerReadAttributeHandler(Option<?> option) {
            this.option = option;
        }

        @Override
        void executeWithWorker(OperationContext context, ModelNode operation, XnioWorker worker) throws OperationFailedException {
            try {
                Object result = worker.getOption(option);
                if (result!=null){
                    context.getResult().set(new ModelNode(result.toString()));
                }
            } catch (IOException e) {
                throw new OperationFailedException(e);
            }
        }
    }

    private static class WorkerMetricsHandler extends AbstractWorkerAttributeHandler{

        @Override
        void executeWithWorker(OperationContext context, ModelNode operation, XnioWorker worker) throws OperationFailedException {
            XnioWorkerMXBean metrics = worker.getMXBean();
            String name = operation.require(ModelDescriptionConstants.NAME).asString();
            context.getResult().set(getMetricValue(name, metrics));
        }
    }

    static XnioWorker getXnioWorker(OperationContext context) {
        String name = context.getCurrentAddressValue();
        if (!context.getCurrentAddress().getLastElement().getKey().equals(PATH.getKey())) { //we are somewhere deeper, lets find worker name
            for (PathElement pe : context.getCurrentAddress()) {
                if (pe.getKey().equals(PATH.getKey())) {
                    name = pe.getValue();
                    break;
                }
            }
        }
        return getXnioWorker(context.getServiceRegistry(false), name);
    }

    static XnioWorker getXnioWorker(ServiceRegistry serviceRegistry, String name) {
        ServiceName serviceName = CAPABILITY.getCapabilityServiceName(name, XnioWorker.class);
        ServiceController<XnioWorker> controller = (ServiceController<XnioWorker>) serviceRegistry.getService(serviceName);
        if (controller == null || controller.getState() != ServiceController.State.UP) {
            return null;
        }
        return controller.getValue();
    }

    private static XnioWorkerMXBean getMetrics(ServiceRegistry serviceRegistry, String name) {
        XnioWorker worker = getXnioWorker(serviceRegistry, name);
        if (worker != null && worker.getMXBean() != null) {
            return worker.getMXBean();
        }
        return null;
    }


    private static ModelNode getMetricValue(String attributeName, XnioWorkerMXBean metric) throws OperationFailedException {
        if (SHUTDOWN_REQUESTED.getName().equals(attributeName)) {
            return new ModelNode(metric.isShutdownRequested());
        } else if (CORE_WORKER_POOL_SIZE.getName().equals(attributeName)) {
            return new ModelNode(metric.getCoreWorkerPoolSize());
        } else if (MAX_WORKER_POOL_SIZE.getName().equals(attributeName)) {
            return new ModelNode(metric.getMaxWorkerPoolSize());
        } else if (IO_THREAD_COUNT.getName().equals(attributeName)) {
            return new ModelNode(metric.getIoThreadCount());
        } else if (QUEUE_SIZE.getName().equals(attributeName)) {
            return new ModelNode(metric.getWorkerQueueSize());
        } else if (BUSY_WORKER_THREAD_COUNT.getName().equals(attributeName)) {
            return new ModelNode(metric.getBusyWorkerThreadCount());
        } else {
            throw new OperationFailedException(IOLogger.ROOT_LOGGER.noMetrics());
        }
    }

    static class WorkerResource extends DelegatingResource {
        private final ServiceRegistry serviceRegistry;
        private final PathAddress pathAddress;

        public WorkerResource(OperationContext context) {
            super(Resource.Factory.create());
            this.serviceRegistry = context.getServiceRegistry(false);
            this.pathAddress = context.getCurrentAddress();
            super.registerResourceProvider("server", new ResourceProvider() {
                @Override
                public boolean has(String name) {
                    return children().contains(name);
                }

                @Override
                public Resource get(String name) {
                    return PlaceholderResource.INSTANCE;
                }

                @Override
                public boolean hasChildren() {
                    return false;
                }

                @Override
                public Set<String> children() {
                    XnioWorkerMXBean metrics = getMetrics(serviceRegistry, pathAddress.getLastElement().getValue());
                    if (metrics == null) {
                        return Collections.emptySet();
                    }
                    Set<String> res = new LinkedHashSet<>();
                    for (XnioServerMXBean serverMXBean : metrics.getServerMXBeans()) {
                        res.add(serverMXBean.getBindAddress());
                    }
                    return res;
                }

                @Override
                public void register(String name, Resource resource) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void register(String value, int index, Resource resource) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Resource remove(String name) {
                    return null;
                }

                @Override
                public ResourceProvider clone() {
                    return this;
                }
            });
        }

        @Override
        public Set<String> getChildTypes() {
            LinkedHashSet<String> result = new LinkedHashSet<>(super.getChildTypes());
            result.add("server");
            return result;
        }

    }
}

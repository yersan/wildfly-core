/*
 * Copyright 2020 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.controller;

import static org.jboss.as.controller.AbstractControllerService.CONSOLE_AVAILABILITY_CAPABILITY;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * Exposes a {@link ConsoleAvailability} to check the availability of the web console.
 * <p>
 *     By default the console is not available, this service listen to changes in the process controller state.
 *     The web console is available as soon as the process controller transitions to running. This service allows
 *     to make the console available even before than the process controller is running by using {@link #setAvailable()}
 *     method.
 * </p>
 *
 * @author @author <a href="mailto:yborgess@redhat.com">Yeray Borges</a>
 */
public class ConsoleAvailabilityService implements Service, ConsoleAvailability, PropertyChangeListener {
    private final Supplier<ProcessStateNotifier> notifier;
    private final Consumer<ConsoleAvailability> serviceConsumer;
    private final LogAdminConsole logAdminConsole;

    private final AtomicStampedReference<Boolean> available = new AtomicStampedReference<>(Boolean.FALSE, 0);

    public ConsoleAvailabilityService(Consumer<ConsoleAvailability> serviceConsumer, Supplier<ProcessStateNotifier> notifier, final LogAdminConsole logMessageAction) {
        this.notifier = notifier;
        this.serviceConsumer = serviceConsumer;
        this.logAdminConsole = logMessageAction;
    }

    public static void addService(ServiceTarget serviceTarget, LogAdminConsole logMessageAction) {
        ServiceName serviceName = CONSOLE_AVAILABILITY_CAPABILITY.getCapabilityServiceName();

        ServiceBuilder<?> sb = serviceTarget.addService(serviceName);
        Consumer<ConsoleAvailability> caConsumer = sb.provides(serviceName);
        Supplier<ProcessStateNotifier> psnSupplier = sb.requires(ControlledProcessStateService.INTERNAL_SERVICE_NAME);

        ConsoleAvailabilityService consoleAvailabilityService = new ConsoleAvailabilityService(caConsumer, psnSupplier, logMessageAction);
        sb.setInstance(consoleAvailabilityService);

        sb.install();
    }

    @Override
    public void start(StartContext context) throws StartException {
        notifier.get().addPropertyChangeListener(this);
        serviceConsumer.accept(this);
    }

    @Override
    public void stop(StopContext context) {
        notifier.get().removePropertyChangeListener(this);
    }

    /**
     *
     * @return
     */
    @Override
    public boolean isAvailable() {
        return this.available.getReference();
    }

    /**
     * Tries to make the console available. The console will be available only if the process controller is not
     * stopping.
     */
    @Override
    public void setAvailable() {
        if (this.available.compareAndSet(Boolean.FALSE, Boolean.TRUE, 0, 1)) {
            logAdminConsole.logAdminConsole();
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (ControlledProcessState.State.RUNNING == evt.getNewValue()) {
            if (this.available.compareAndSet(Boolean.FALSE, Boolean.TRUE, 0, 1)) {
                logAdminConsole.logAdminConsole();
            }
        } else if (ControlledProcessState.State.STOPPING == evt.getNewValue()) {
            this.available.set(Boolean.FALSE, 1);
        } else if (ControlledProcessState.State.STARTING == evt.getNewValue()) {
            this.available.set(Boolean.FALSE, 0);
        } else if (ControlledProcessState.State.STOPPED == evt.getNewValue()) {
            this.available.set(Boolean.FALSE, 0);
        }
    }

    @FunctionalInterface
    public interface LogAdminConsole {
        void logAdminConsole();
    }
}

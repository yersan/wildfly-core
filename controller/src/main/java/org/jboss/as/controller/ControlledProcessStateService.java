/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.function.Consumer;

import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.core.embedded.spi.EmbeddedProcessState;

/**
 * Exposes the current {@link ControlledProcessState.State} and allows services to register a listener for changes
 * to it.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class ControlledProcessStateService implements ProcessStateNotifier, org.wildfly.core.embedded.spi.ProcessStateNotifier {

    /** @deprecated use the 'org.wildfly.management.process-state-notifier' capability to obtain a {@link ProcessStateNotifier}*/
    @Deprecated(forRemoval = true)
    public static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("controlled-process-state");
    /** Only for use within the WildFly Core kernel; may change or be removed at any time */
    public static final ServiceName INTERNAL_SERVICE_NAME = AbstractControllerService.PROCESS_STATE_NOTIFIER_CAPABILITY.getCapabilityServiceName();

    /**
     * Obtains a {@link ProcessStateNotifier} linked to the given {@code processState} object
     * and installs an MSC {@link Service} that provides it as its value.
     *
     * @param target service target to use to install the service. Cannot be {@code null}.
     * @param processState {@link ControlledProcessState} instance whose changes will be tracked by the returned notifier.
     *
     * @return the {@link ProcessStateNotifier} that is the value of the installed service.
     */
    public static ProcessStateNotifier addService(ServiceTarget target, ControlledProcessState processState) {
        final ControlledProcessStateService notifier = processState.getService();
        final ServiceBuilder<?> sb = target.addService();
        Consumer<ProcessStateNotifier> consumer = sb.provides(INTERNAL_SERVICE_NAME, SERVICE_NAME, org.wildfly.core.embedded.spi.ProcessStateNotifier.SERVICE_NAME);
        sb.setInstance(Service.newInstance(consumer, notifier));
        sb.install();
        return notifier;
    }

    private ControlledProcessState.State processState;
    private final PropertyChangeSupport changeSupport;
    private final boolean embedded;

    ControlledProcessStateService(ControlledProcessState.State initialState, boolean embedded) {
        this.processState = initialState;
        this.changeSupport = new PropertyChangeSupport(this);
        this.embedded = embedded;
    }

    /**
     * Returns the current process state.
     *
     * @return  the current state
     */
    @Override
    public ControlledProcessState.State getCurrentState() {
        return processState;
    }

    /**
     * Returns the current process state.
     *
     * @return  the current state
     */
    @Override
    public EmbeddedProcessState getEmbeddedProcessState() {
        checkEmbedded();
        return processState.getEmbeddedProcessState();
    }

    /**
     * Registers a listener for changes to the process state.
     *
     * @param listener the listener
     */
    @Override
    public void addPropertyChangeListener(
            PropertyChangeListener listener) {
        changeSupport.addPropertyChangeListener("currentState", listener);
    }

    /**
     * Removes a previously {@link #addPropertyChangeListener(PropertyChangeListener) registered listener}.
     *
     * @param listener the listener
     */
    @Override
    public void removePropertyChangeListener(
            PropertyChangeListener listener) {
        changeSupport.removePropertyChangeListener("currentState", listener);
    }

    @Override
    public void addProcessStateListener(PropertyChangeListener listener) {
        checkEmbedded();
        changeSupport.addPropertyChangeListener("embeddedState", listener);
    }

    @Override
    public void removeProcessStateListener(PropertyChangeListener listener) {
        checkEmbedded();
        changeSupport.removePropertyChangeListener("embeddedState", listener);
    }

    synchronized void stateChanged(ControlledProcessState.State newState) {
        final ControlledProcessState.State oldState = processState;
        processState = newState;
        changeSupport.firePropertyChange("currentState", oldState, newState);
        changeSupport.firePropertyChange("embeddedState", oldState.getEmbeddedProcessState(), newState.getEmbeddedProcessState());
    }

    private void checkEmbedded() {
        if (!embedded) {
            throw new IllegalStateException();
        }
    }
}

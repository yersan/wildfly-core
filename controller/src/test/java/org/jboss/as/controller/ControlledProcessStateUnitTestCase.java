/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.controller;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests of {@link ControlledProcessState}.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class ControlledProcessStateUnitTestCase {

    /** Test the AS7-1103 scenario */
    @Test
    public void testSetRunningRequiresStarting() {

        ControlledProcessState state = new ControlledProcessState(true);
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
        Object stamp = state.setRestartRequired();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
        state.setRunning(); // in AS7-1103 bug, another thread did this
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
        state.revertRestartRequired(stamp);
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());

        state = new ControlledProcessState(true);
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
        stamp = state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.RELOAD_REQUIRED, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RELOAD_REQUIRED, state.getState());
        state.revertReloadRequired(stamp);
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
    }

    /** Test the AS7-5929 scenario -- a reload should not clear RESTART_REQUIRED state */
    @Test
    public void testRestartRequiredRequiresRestart() {

        ControlledProcessState state = new ControlledProcessState(true);
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
        state.setRestartRequired();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());

        // Now simulate a :reload
        state.setStopping();
        state.setStarting();
        state.setRunning();

        // Validate the RESTART_REQUIRED state still pertains
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());

    }

    @Test
    public void testRestartRequiredRequiresRestart_OnStarting() {

        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRestartRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());

        // Now simulate a :reload
        state.setStopping();
        state.setStarting();
        state.setRunning();

        // Validate the RESTART_REQUIRED state still pertains
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());

    }

    @Test
    public void test_restartRequired_OnStarting() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRestartRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());

        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
    }

    @Test
    public void test_revert_restartRequired_OnStarting() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        Object stamp = state.setRestartRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.revertRestartRequired(stamp);
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
    }

    @Test
    public void test_revert_restartRequired_OnStarting_OnRunning() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        Object stamp = state.setRestartRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
        state.revertRestartRequired(stamp);
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
    }

    @Test
    public void test_reloadRequired_OnStarting() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RELOAD_REQUIRED, state.getState());

        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RELOAD_REQUIRED, state.getState());
    }

    @Test
    public void test_revert_reloadRequired_OnStarting() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        Object stamp = state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.revertReloadRequired(stamp);
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
    }

    @Test
    public void test_revert_reloadRequired_OnStarting_OnRunning() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        Object stamp = state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RELOAD_REQUIRED, state.getState());
        state.revertReloadRequired(stamp);
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
    }

    @Test
    public void test_reloadRequired_notSupported_OnStarting() {
        ControlledProcessState state = new ControlledProcessState(false);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
    }

    @Test
    public void test_revert_reloadRequired_notSupported_OnStarting() {
        ControlledProcessState state = new ControlledProcessState(false);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        Object stamp = state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.revertReloadRequired(stamp);
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
    }

    @Test
    public void test_revert_reloadRequired__notSupported_OnStarting_OnRunning() {
        ControlledProcessState state = new ControlledProcessState(false);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        Object stamp = state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
        state.revertReloadRequired(stamp);
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
    }

    @Test
    public void testNormalStartStop() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
        state.setStopping();
        Assert.assertEquals(ControlledProcessState.State.STOPPING, state.getState());
        state.setStopped();
        Assert.assertEquals(ControlledProcessState.State.STOPPED, state.getState());
    }

    @Test
    public void test_reloadRequired_OnRestartRequired() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
        state.setRestartRequired();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
        state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
    }

    @Test
    public void test_reloadRequired_OnRestartRequired_OnStarting() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRestartRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
    }

    @Test
    public void test_reloadRequired_OnRestartRequired_OnStarting_OnRunning() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRestartRequired();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
        state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
    }

    @Test
    public void test_reloadRequired_OnRestartRequired_OnRunning() {
        ControlledProcessState state = new ControlledProcessState(true);
        state.setStarting();
        Assert.assertEquals(ControlledProcessState.State.STARTING, state.getState());
        state.setRunning();
        Assert.assertEquals(ControlledProcessState.State.RUNNING, state.getState());
        state.setRestartRequired();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
        state.setReloadRequired();
        Assert.assertEquals(ControlledProcessState.State.RESTART_REQUIRED, state.getState());
    }
}

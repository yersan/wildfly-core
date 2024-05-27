/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.standalone.mgmt;

import static org.jboss.as.test.shared.TimeoutUtil.adjust;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.inject.Inject;
import org.jboss.as.test.integration.management.util.CLIWrapper;

import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.common.function.ExceptionRunnable;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Test case to test resource limits and clean up of management interface connections.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class ManagementInterfaceResourcesTestCase {
    private static final Logger LOG = Logger.getLogger(ManagementInterfaceResourcesTestCase.class.getName());

    private static final String BACKLOG_PROPERTY = "org.wildfly.management.backlog";
    private static final String CONNECTION_HIGH_WATER_PROPERTY = "org.wildfly.management.connection-high-water";
    private static final String CONNECTION_LOW_WATER_PROPERTY = "org.wildfly.management.connection-low-water";
    private static final String NO_REQUEST_TIMEOUT_PROPERTY = "org.wildfly.management.no-request-timeout";

    @Inject
    protected static ServerController controller;

    /**
     * Test that the management interface will not accept new connections when the number of active connections reaches the
     * high water mark.  After the number of open connections has been reduced to the low watermark it will test that connections
     * are accepted again.
     */
    @Test
    public void testWatermarks() throws Exception {
        runTest(60000, () -> {
            String mgmtAddress = controller.getClient().getMgmtAddress();
            int mgmtPort = controller.getClient().getMgmtPort();
            LOG.info(mgmtAddress + ":" + mgmtPort);
            LOG.info(TestSuiteEnvironment.getServerAddress() + ":" + TestSuiteEnvironment.getServerPort());
            SocketAddress targetAddress = new InetSocketAddress(TestSuiteEnvironment.getServerAddress(), TestSuiteEnvironment.getServerPort());


            int failedConnections = 0;
            int socketsOpened = 0;
            boolean oneFailed = false;
            Socket[] sockets = new Socket[9];
            for (int i = 0 ; i < 9 ; ) {
                LOG.info("Opening socket " + i + " socketsOpened=" + socketsOpened);
                try {
                    sockets[i] = new Socket();
                    sockets[i].connect(targetAddress, 5000);

                    socketsOpened++;
                    i++;
                } catch (SocketTimeoutException stoe) {
                    LOG.log(Level.SEVERE, "Probably an expected Socket timeout", stoe);
                    assertTrue("Less sockets than low watermark opened.", socketsOpened > 3);
                    oneFailed = true;
                    i++;
                } catch (IOException ioe) {
                    // A connection error, try again creating a new socket for the same position
                    failedConnections++;
                    LOG.log(Level.SEVERE, "Connection error for the Socket at position " + i, ioe);
                }
                if (failedConnections > 5) {
                    fail("Failed connections exceeded 5, aborting test.");
                }
            }
            assertTrue("Opening of one socket was expected to fail.", oneFailed);

            // Now close the connections and we should be able to connect again.
            for (int i = 0 ; i < socketsOpened ; i++) {
                sockets[i].close();
            }

            Socket goodSocket = new Socket();
            // This needs a reasonable time to give the server time to respond to the closed connections.
            goodSocket.connect(targetAddress, 10000);
            goodSocket.close();
        });
    }

    @Test
    public void testTimeout() throws Exception {
        runTest(10000, () -> {
            String mgmtAddress = controller.getClient().getMgmtAddress();
            int mgmtPort = controller.getClient().getMgmtPort();
            SocketAddress targetAddress = new InetSocketAddress(mgmtAddress, mgmtPort);

            int failedConnections = 0;
            int socketsOpened = 0;
            boolean oneFailed = false;
            Socket[] sockets = new Socket[9];
            for (int i = 0 ; i < 9 ;) {
                LOG.info("Opening socket " + i + " socketsOpened=" + socketsOpened);
                try {
                    sockets[i] = new Socket();
                    sockets[i].connect(targetAddress, 5000);
                    socketsOpened++;
                    i++;
                } catch (SocketTimeoutException stoe) {
                    LOG.log(Level.SEVERE, "Probably an expected Socket timeout", stoe);
                    assertTrue("Less sockets than low watermark opened.", socketsOpened > 3);
                    oneFailed = true;
                    i++;
                } catch (IOException ioe) {
                    // A connection error, try again creating a new socket for the same position
                    failedConnections++;
                    LOG.log(Level.SEVERE, "Connection error for the Socket at position " + i, ioe);
                }
                if (failedConnections > 5) {
                    fail("Failed connections exceeded 5, aborting test.");
                }
            }
            assertTrue("Opening of one socket was expected to fail.", oneFailed);

            Thread.sleep(adjust(10000)); // We also had 5000ms for each bad socket that triggered a timeout.

            Socket goodSocket = new Socket();
            // This needs to be longer than 500ms to give the server time to respond to the closed connections.
            goodSocket.connect(targetAddress, 10000);
            goodSocket.close();

            // Clean up remaining sockets
            for (int i = 0 ; i < socketsOpened ; i++) {
                sockets[i].close();
            }
        });
    }

    private void runTest(int noRequestTimeout, ExceptionRunnable<Exception> test) throws Exception {
        controller.startInAdminMode();
        try (CLIWrapper cli = new CLIWrapper(true)) {
            cli.sendLine(String.format("/system-property=%s:add(value=%d)", BACKLOG_PROPERTY, 2));
            cli.sendLine(String.format("/system-property=%s:add(value=%d)", CONNECTION_HIGH_WATER_PROPERTY, 5));
            cli.sendLine(String.format("/system-property=%s:add(value=%d)", CONNECTION_LOW_WATER_PROPERTY, 3));
            cli.sendLine(String.format("/system-property=%s:add(value=%d)", NO_REQUEST_TIMEOUT_PROPERTY, noRequestTimeout));
        }

        try {
            controller.reload();

            test.run();
        } finally {
            controller.reload();

            try (CLIWrapper cli = new CLIWrapper(true)) {
                cli.sendLine(String.format("/system-property=%s:remove()", BACKLOG_PROPERTY));
                cli.sendLine(String.format("/system-property=%s:remove()", CONNECTION_HIGH_WATER_PROPERTY));
                cli.sendLine(String.format("/system-property=%s:remove()", CONNECTION_LOW_WATER_PROPERTY));
                cli.sendLine(String.format("/system-property=%s:remove()", NO_REQUEST_TIMEOUT_PROPERTY));
            }
            controller.stop();
        }
    }

}

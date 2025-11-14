package org.jboss.as.test.manualmode.secman;

import java.util.Collection;
import java.util.Collections;

import jakarta.inject.Inject;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.logging.LoggingUtil;
import org.jboss.as.test.shared.logging.TestLogHandlerSetupTask;
import org.jboss.logmanager.Level;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;

@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class VerifyTestCase {

    @Inject
    private ServerController container;
    private ModelControllerClient client;
    private final LogHandlerSetup logHandlerSetup = new LogHandlerSetup();

    @Before
    public void setUp() throws Exception {
        container.start();
        client = TestSuiteEnvironment.getModelControllerClient();
        logHandlerSetup.setup(client);
    }

    @After
    public void tearDown() throws Exception {
        logHandlerSetup.tearDown(client);
        container.stop();
    }


    @Test
    public void testVerifyStop() throws Exception {
        Assert.assertTrue(container.isStarted());
        // INFO  [org.jboss.as.server] (Management Triggered Shutdown) WFLYSRV0241: Shutting down in response to management operation 'shutdown'
        Assert.assertFalse(LoggingUtil.hasLogMessage(this.client, VerifyTestCase.class.getSimpleName(), "WFLYSRV0241"));
        container.stop();
        Assert.assertFalse(container.isStarted());
        container.start();
        Assert.assertTrue(LoggingUtil.hasLogMessage(this.client, VerifyTestCase.class.getSimpleName(), "WFLYSRV0241"));
        Assert.assertTrue(container.isStarted());
    }

    @Test
    public void testVerifyReload() throws Exception {
        Assert.assertTrue(container.isStarted());
        // 16:51:51,035 INFO  [org.jboss.as] (MSC service thread 1-8) WFLYSRV0050: WildFly Core 31.0.0.Beta3-SNAPSHOT stopped in 12ms
        Assert.assertFalse(LoggingUtil.hasLogMessage(this.client, VerifyTestCase.class.getSimpleName(), "WFLYSRV0050"));
        ServerReload.executeReloadAndWaitForCompletion(client);
        Assert.assertTrue(LoggingUtil.hasLogMessage(this.client, VerifyTestCase.class.getSimpleName(), "WFLYSRV0050"));
        Assert.assertTrue(container.isStarted());
    }



    private static class LogHandlerSetup extends TestLogHandlerSetupTask {

        @Override
        public Collection<String> getCategories() {
            return Collections.singleton("org.jboss.as");
        }

        @Override
        public String getLevel() {
            return Level.DEBUG.getName();
        }

        @Override
        public String getHandlerName() {
            return VerifyTestCase.class.getSimpleName();
        }

        @Override
        public String getLogFileName() {
            return VerifyTestCase.class.getSimpleName() + ".log";
        }
    }
}

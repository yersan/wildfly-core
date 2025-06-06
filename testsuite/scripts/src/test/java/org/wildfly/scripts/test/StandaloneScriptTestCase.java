/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.scripts.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.json.JsonObject;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.wildfly.common.test.ServerConfigurator;
import org.wildfly.common.test.ServerHelper;
import org.wildfly.common.test.LoggingAgent;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(Parameterized.class)
public class StandaloneScriptTestCase extends ScriptTestCase {
    private static final String STANDALONE_BASE_NAME = "standalone";
    private static final String POWERSHELL = "powershell";

    @Parameter
    public Map<String, String> env;

    private static final Function<ModelControllerClient, Boolean> STANDALONE_CHECK = ServerHelper::isStandaloneRunning;

    public StandaloneScriptTestCase() {
        super(STANDALONE_BASE_NAME);
    }

    @Parameters
    public static Collection<Object> data() {
        return List.of(
                Map.of(),
                Map.of("GC_LOG", "true"),
                Map.of("MODULE_OPTS", "-javaagent:logging-agent-tests.jar=" + LoggingAgent.DEBUG_ARG),
                Map.of("SECMGR", SECMGR_VALUE)
        );
    }

    @Test
    public void testBatchScriptJavaOptsToEscape() throws Exception {
        Assume.assumeTrue(TestSuiteEnvironment.isWindows());
        final String variableToEscape = "-Dhttp.nonProxyHosts=localhost|127.0.0.1|10.10.10.*";
        ServerConfigurator.appendJavaOpts(ServerHelper.JBOSS_HOME, "standalone", variableToEscape);
        try (ScriptProcess script = new ScriptProcess(ServerHelper.JBOSS_HOME, STANDALONE_BASE_NAME, Shell.BATCH, ServerHelper.TIMEOUT)) {
            testScript(script);
        }
        ServerConfigurator.removeJavaOpts(ServerHelper.JBOSS_HOME, "standalone", variableToEscape);
    }

    @Test
    public void testBatchScriptJavaOptsEscaped() throws Exception {
        Assume.assumeTrue(TestSuiteEnvironment.isWindows());
        final String escapedVariable = "-Dhttp.nonProxyHosts=localhost^|127.0.0.1^|10.10.10.*";
        ServerConfigurator.appendJavaOpts(ServerHelper.JBOSS_HOME, STANDALONE_BASE_NAME, escapedVariable);
        try (ScriptProcess script = new ScriptProcess(ServerHelper.JBOSS_HOME, STANDALONE_BASE_NAME, Shell.BATCH, ServerHelper.TIMEOUT)) {
            testScript(script);
        }
        ServerConfigurator.removeJavaOpts(ServerHelper.JBOSS_HOME, STANDALONE_BASE_NAME, escapedVariable);
    }

    @Test
    public void testWFCORE5917InWindows() throws Exception {
        // WFCORE-5917
        // On Windows, command-line arguments of the process can be obtained only via PowerShell.
        // For Windows, where PowerShell is not available, testScript WFCORE-5917 related assertions are skipped.
        // The purpose of this test is to determine whether WFCORE-5917 has been verified.
        if (TestSuiteEnvironment.isWindows()) {
            Assume.assumeTrue(Shell.POWERSHELL.isSupported());
        }
    }

    @Override
    void testScript(final ScriptProcess script) throws InterruptedException, TimeoutException, IOException {
        // This is an odd case for Windows where with the -Xlog:gc* where the file argument does not seem to work with
        // a directory that contains a space. It seems similar to https://bugs.openjdk.java.net/browse/JDK-8215398
        // however the workaround is to do something like file=`\`"C:\wildfly\standalong\logs\gc.log`\`". This does not
        // seem to work when a directory has a space. An error indicating the trailing quote cannot be found. Removing
        // the `\ parts and just keeping quotes ends in the error shown in JDK-8215398.
        Assume.assumeFalse(TestSuiteEnvironment.isWindows() && env.containsKey("GC_LOG") && script.getScript().toString().contains(" "));

        // Test WFCORE-5917 by adding the jboss.server.base.dir argument
        // Due to WFCORE-6684, skip this verification if there are whitespaces in the home directory
        if (!script.getContainerHome().toString().contains(" ")) {
            List<String> args = new ArrayList<>(Arrays.asList(ServerHelper.DEFAULT_SERVER_JAVA_OPTS));
            Path sbdPath = script.getContainerHome().resolve("standalone");
            args.add("-Djboss.server.base.dir=" + sbdPath);
            script.start(STANDALONE_CHECK, env, args.toArray(String[]::new));
        } else {
            script.start(STANDALONE_CHECK, env, ServerHelper.DEFAULT_SERVER_JAVA_OPTS);
        }

        Assert.assertNotNull("The process is null and may have failed to start.", script);
        Assert.assertTrue("The process is not running and should be", script.isAlive());

        final var stdout = script.getStdoutAsString();
        if (supportsEnhancedSecurityManager() && env.containsKey("SECMGR")) {
            Assert.assertTrue("Expected to find -Djava.security.manager=allow in the JVM parameters.", stdout.contains("-Djava.security.manager=allow"));
        } else {
            Assert.assertFalse("Did not expect to find -Djava.security.manager=allow in the JVM parameters.", stdout.contains("-Djava.security.manager=allow"));
        }

        // test WFCORE-5917
        if (!script.getContainerHome().toString().trim().contains(" ")) {
            ProcessHandle handle = script.toHandle();
            List<ProcessHandle> children = handle.children().collect(Collectors.toList());

            String launchCommand = "";
            if (TestSuiteEnvironment.isWindows()) {
                if (Shell.POWERSHELL.isSupported()) {
                    ProcessHandle serverProcess = children.get(1);
                    launchCommand = getWindowsProcessCommandLine(serverProcess);
                    Assert.assertFalse("Server process launch command is not available", launchCommand.isEmpty());
                }
            } else {
                Assert.assertEquals("The standalone process should have started one single process.", 1, children.size());
                ProcessHandle serverProcess = children.get(0);
                Optional<String> commandLine = serverProcess.info().commandLine();
                Assert.assertFalse("Server process launch command is not available", commandLine.isEmpty());
                launchCommand = commandLine.get();
            }

            if (!launchCommand.isEmpty()) {
                String[] serverArgs = launchCommand.split("\\s+");
                int occurrences = 0;
                outer:
                for (int i = 0; i < serverArgs.length; i++) {
                    if ( !serverArgs[i].contains("-Djboss.server.base.dir=") ) {
                        continue;
                    }
                    occurrences++;
                    for (int j = i+1; j <serverArgs.length; j++) {
                        if (serverArgs[i].equals(serverArgs[j])){
                            occurrences++;
                            break outer;
                        }
                    }
                }
                Assert.assertEquals("Found duplicate server jboss.server.base.dir argument in the server launch command. Launch command is " + launchCommand, 1, occurrences);
            }
        }

        if (env.containsKey("MODULE_OPTS")) {
            final List<JsonObject> lines = ServerHelper.readLogFileFromModel("json.log");
            Assert.assertEquals("Expected 2 lines found " + lines.size(), 2, lines.size());
            JsonObject msg = lines.get(0);
            Assert.assertEquals(LoggingAgent.MSG, msg.getString("message"));
            Assert.assertEquals("FINE", msg.getString("level"));
            msg = lines.get(1);
            Assert.assertEquals(LoggingAgent.MSG, msg.getString("message"));
            Assert.assertEquals("INFO", msg.getString("level"));
        }

        // Shutdown the server
        @SuppressWarnings("Convert2Lambda")
        final Callable<ModelNode> callable = new Callable<ModelNode>() {
            @Override
            public ModelNode call() throws Exception {
                try (ModelControllerClient client = TestSuiteEnvironment.getModelControllerClient()) {
                    return executeOperation(client, Operations.createOperation("shutdown"));
                }
            }
        };
        execute(callable);
        validateProcess(script);

        // Check if the gc.log exists assuming we have the GC_LOG environment variable added
        if ("true".equals(env.get("GC_LOG"))) {
            final Path logDir = script.getContainerHome().resolve("standalone").resolve("log");
            Assert.assertTrue(Files.exists(logDir));
            final String fileName = "gc.log";
            Assert.assertTrue(String.format("Missing %s file in %s", fileName, logDir), Files.exists(logDir.resolve(fileName)));
        }
    }

    private String getWindowsProcessCommandLine(ProcessHandle serverProcess) throws IOException {
        String launchCommand = "";

        ProcessBuilder builder = new ProcessBuilder(POWERSHELL, "Get-CimInstance Win32_Process -Filter \\\"ProcessId  = " + serverProcess.pid() + "\\\" | select CommandLine | Out-String -width 9999");
        Process powerShellProcess = builder.start();
        powerShellProcess.getOutputStream().close();
        String line;
        BufferedReader stdout = new BufferedReader(new InputStreamReader(powerShellProcess.getInputStream()));
        while ((line = stdout.readLine()) != null) {
            if (line.contains("standalone")) {
                launchCommand = line;
                break;
            }
        }
        stdout.close();

        return launchCommand;
    }
}

/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.model.jvm;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="ropalka@redhat.com">Richard Opalka</a>
 */
public class JvmOptionsBuilderUnitTestCase {

    private static final JvmOptionsBuilderFactory FACTORY = JvmOptionsBuilderFactory.getInstance(org.jboss.as.host.controller.jvm.JvmType.createFromSystemProperty(true));

    @Test
    public void testNoOptionsSun() {
        testNoOptions(JvmType.ORACLE);
    }

    @Test
    public void testNoOptionsIbm() {
        testNoOptions(JvmType.IBM);
    }

    private void testNoOptions(JvmType type) {
        JvmElement element = JvmElementTestUtils.create(type);

        List<String> command = new ArrayList<>();
        FACTORY.addOptions(element, command);

        Assert.assertEquals(0, command.size());
    }

    @Test
    public void testDebugOptionsNotEnabledSun() {
        testDebugOptionsNotEnabled(JvmType.ORACLE);
    }

    @Test
    public void testDebugOptionsNotEnabledIbm() {
        testDebugOptionsNotEnabled(JvmType.IBM);
    }

    private void testDebugOptionsNotEnabled(JvmType type) {
        JvmElement element = JvmElementTestUtils.create(type);
        JvmElementTestUtils.setDebugEnabled(element, false);
        JvmElementTestUtils.setDebugOptions(element, "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n");

        List<String> command = new ArrayList<String>();
        FACTORY.addOptions(element, command);

        Assert.assertEquals(0, command.size());
    }

    @Test
    public void testDebugOptionsAndEnabledSun() {
        testDebugOptionsAndEnabled(JvmType.ORACLE);
    }

    @Test
    public void testDebugOptionsAndEnabledIbm() {
        testDebugOptionsAndEnabled(JvmType.IBM);
    }

    private void testDebugOptionsAndEnabled(JvmType type) {
        JvmElement element = JvmElementTestUtils.create(type);
        JvmElementTestUtils.setDebugEnabled(element, true);
        JvmElementTestUtils.setDebugOptions(element, "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n");

        List<String> command = new ArrayList<String>();
        FACTORY.addOptions(element, command);

        Assert.assertEquals(1, command.size());
        Assert.assertTrue(command.contains("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n"));
    }

    @Test
    public void testHeapSun() {
        testHeap(JvmType.ORACLE);
    }

    @Test
    public void testHeapIbm() {
        testHeap(JvmType.IBM);
    }

    private void testHeap(JvmType type) {
        JvmElement element = JvmElementTestUtils.create(type);
        JvmElementTestUtils.setHeapSize(element, "28M");
        JvmElementTestUtils.setMaxHeap(element, "96M");

        List<String> command = new ArrayList<String>();
        FACTORY.addOptions(element, command);

        Assert.assertEquals(2, command.size());
        Assert.assertTrue(command.contains("-Xms28M"));
        Assert.assertTrue(command.contains("-Xmx96M"));
    }

    @Test
    public void testPermgenSun() {
        JvmElement element = JvmElementTestUtils.create(JvmType.ORACLE);
        JvmElementTestUtils.setPermgenSize(element, "32M");
        JvmElementTestUtils.setMaxPermgen(element, "64M");

        List<String> command = new ArrayList<String>();
        FACTORY.addOptions(element, command);

        Assert.assertEquals(0, command.size());

    }

    @Test
    public void testPermgenIbm() {
        JvmElement element = JvmElementTestUtils.create(JvmType.IBM);
        JvmElementTestUtils.setPermgenSize(element, "32M");
        JvmElementTestUtils.setMaxPermgen(element, "64M");

        List<String> command = new ArrayList<String>();
        FACTORY.addOptions(element, command);

        Assert.assertEquals(0, command.size());
    }

    @Test
    public void testJDK9Params() {
        JvmElement element = JvmElementTestUtils.create(JvmType.ORACLE);

        element.getJvmOptions().addOption("--add-exports=java.base/sun.nio.ch=ALL-UNNAMED");
        element.getJvmOptions().addOption("--add-opens=java.base/sun.nio.ch=ALL-UNNAMED");
        element.getJvmOptions().addOption("--add-reads=java.base/sun.nio.ch=ALL-UNNAMED");
        element.getJvmOptions().addOption("--illegal-access=warn");
        List<String> command = new ArrayList<String>();
        FACTORY.addOptions(element, command);
        Assert.assertEquals(4, command.size());
    }

    @Test
    public void testStackSun() {
        testStack(JvmType.ORACLE);
    }

    @Test
    public void testStackIbm() {
        testStack(JvmType.IBM);
    }

    private void testStack(JvmType type) {
        JvmElement element = JvmElementTestUtils.create(type);
        JvmElementTestUtils.setStack(element, "1M");

        List<String> command = new ArrayList<String>();
        FACTORY.addOptions(element, command);

        Assert.assertEquals(1, command.size());
        Assert.assertTrue(command.contains("-Xss1M"));
    }

    @Test
    public void testAgentLibSun() {
        testAgentLib(JvmType.ORACLE);
    }

    @Test
    public void testAgentLibIbm() {
        testAgentLib(JvmType.IBM);
    }

    private void testAgentLib(JvmType type) {
        JvmElement element = JvmElementTestUtils.create(type);
        JvmElementTestUtils.setAgentLib(element, "blah=x");

        List<String> command = new ArrayList<String>();
        FACTORY.addOptions(element, command);

        Assert.assertEquals(1, command.size());
        Assert.assertTrue(command.contains("-agentlib:blah=x"));
    }


    @Test
    public void testAgentPathSun() {
        testAgentPath(JvmType.ORACLE);
    }

    @Test
    public void testAgentPathIbm() {
        testAgentPath(JvmType.IBM);
    }

    private void testAgentPath(JvmType type) {
        JvmElement element = JvmElementTestUtils.create(type);
        JvmElementTestUtils.setAgentPath(element, "blah.jar=x");

        List<String> command = new ArrayList<String>();
        FACTORY.addOptions(element, command);

        Assert.assertEquals(1, command.size());
        Assert.assertTrue(command.contains("-agentpath:blah.jar=x"));
    }

    @Test
    public void testJavaagentSun() {
        testJavaagent(JvmType.ORACLE);
    }

    @Test
    public void testJavaagentIbm() {
        testJavaagent(JvmType.IBM);
    }

    private void testJavaagent(JvmType type) {
        JvmElement element = JvmElementTestUtils.create(type);
        JvmElementTestUtils.setJavaagent(element, "blah.jar=x");

        List<String> command = new ArrayList<String>();
        FACTORY.addOptions(element, command);

        Assert.assertEquals(1, command.size());
        Assert.assertTrue(command.contains("-javaagent:blah.jar=x"));
    }

    @Test
    public void testJvmOptionsSun() {
        testJvmOptions(JvmType.ORACLE);
    }

    @Test
    public void testJvmOptionsIbm() {
        testJvmOptions(JvmType.IBM);
    }

    private void testJvmOptions(JvmType type) {
        JvmElement element = JvmElementTestUtils.create(type);
        JvmElementTestUtils.addJvmOption(element, "-Xblah1=yes");
        JvmElementTestUtils.addJvmOption(element, "-Xblah2=no");

        List<String> command = new ArrayList<String>();
        FACTORY.addOptions(element, command);

        Assert.assertEquals(2, command.size());
        Assert.assertTrue(command.contains("-Xblah1=yes"));
        Assert.assertTrue(command.contains("-Xblah2=no"));
    }

    @Test
    public void testJvmOptionsIgnoredWhenInMainSchemaSun() {
        testJvmOptionsIgnoredWhenInMainSchema(JvmType.ORACLE);
    }

    @Test
    public void testJvmOptionsIgnoredWhenInMainSchemaIbm() {
        testJvmOptionsIgnoredWhenInMainSchema(JvmType.IBM);
    }

    private void testJvmOptionsIgnoredWhenInMainSchema(JvmType type) {
        JvmElement element = JvmElementTestUtils.create(type);

        //Main schema
        JvmElementTestUtils.setDebugEnabled(element, true);
        JvmElementTestUtils.setDebugOptions(element, "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n");
        JvmElementTestUtils.setHeapSize(element, "28M");
        JvmElementTestUtils.setMaxHeap(element, "96M");
        JvmElementTestUtils.setPermgenSize(element, "32M");
        JvmElementTestUtils.setMaxPermgen(element, "64M");
        JvmElementTestUtils.setStack(element, "1M");
        JvmElementTestUtils.setAgentLib(element, "blah=x");
        JvmElementTestUtils.setJavaagent(element, "blah.jar=x");
        //Options
        JvmElementTestUtils.addJvmOption(element, "-Xblah1=yes");
        JvmElementTestUtils.addJvmOption(element, "-Xblah2=no");
        //Ignored options
        JvmElementTestUtils.addJvmOption(element, "-agentlib:jdwp=ignoreme");
        JvmElementTestUtils.addJvmOption(element, "-Xms1024M");
        JvmElementTestUtils.addJvmOption(element, "-Xmx1024M");
        JvmElementTestUtils.addJvmOption(element, "-XX:PermSize=1024M");
        JvmElementTestUtils.addJvmOption(element, "-XX:MaxPermSize=1024M");
        JvmElementTestUtils.addJvmOption(element, "-Xss100M");
        JvmElementTestUtils.addJvmOption(element, "-agentlib:other=x");
        JvmElementTestUtils.addJvmOption(element, "-javaagent:other.jar=x");

        List<String> command = new ArrayList<String>();
        FACTORY.addOptions(element, command);

        Assert.assertEquals( 8, command.size());
        Assert.assertTrue(command.contains("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n"));
        Assert.assertTrue(command.contains("-Xms28M"));
        Assert.assertTrue(command.contains("-Xmx96M"));
        Assert.assertTrue(command.contains("-Xss1M"));
        Assert.assertTrue(command.contains("-agentlib:blah=x"));
        Assert.assertTrue(command.contains("-javaagent:blah.jar=x"));
        Assert.assertTrue(command.contains("-Xblah1=yes"));
        Assert.assertTrue(command.contains("-Xblah2=no"));
    }
}

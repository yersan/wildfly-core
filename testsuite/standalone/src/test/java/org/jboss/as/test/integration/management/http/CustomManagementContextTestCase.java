/*
Copyright 2016 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.jboss.as.test.integration.management.http;

import jakarta.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.test.integration.management.extension.customcontext.testbase.CustomManagementContextTestBase;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Test of integrating a custom management context on the http interface on a standalone server.
 *
 * @author Brian Stansberry
 */
@RunWith(WildFlyRunner.class)
public class CustomManagementContextTestCase extends CustomManagementContextTestBase {

    @Inject
    private ManagementClient managementClient;

    @Override
    protected PathAddress getExtensionAddress() {
        return EXT;
    }

    @Override
    protected PathAddress getSubsystemAddress() {
        return PathAddress.pathAddress(SUB);
    }

    @Override
    protected ManagementClient getManagementClient() {
        return managementClient;
    }
}

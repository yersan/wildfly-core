package org.jboss.as.test.integration.domain.suspendresume;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class PrintTestNameRule extends TestWatcher {
    @Override
    protected void starting(Description d) {
        System.out.println(" -----------------------------> " + d.getMethodName());
    }
}

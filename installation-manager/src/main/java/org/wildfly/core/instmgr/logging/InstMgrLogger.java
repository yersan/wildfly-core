package org.wildfly.core.instmgr.logging;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

import java.io.IOException;

import static org.jboss.logging.Logger.Level.WARN;

@SuppressWarnings("DefaultAnnotationParam")
@MessageLogger(projectCode = "WFLYIM", length = 4)
public interface InstMgrLogger extends BasicLogger {
    InstMgrLogger ROOT_LOGGER = Logger.getMessageLogger(InstMgrLogger.class, " org.wildfly.core.installationmanager");

    @Message(id = 1, value = "No known attribute %s")
    OperationFailedException unknownAttribute(String asString);

    @LogMessage(level = WARN)
    @Message(id = 2, value = "No known attribute %s")
    void test(String asString);

    @Message(id = 3, value = "Zip entry %s is outside of the target dir %s")
    IOException zipEntryOutsideOfTarget(String entry, String target);
    @Message(id = 4, value = "Cannot perform the Operation because there is an update in progress")
    OperationFailedException updateInProgress();
}

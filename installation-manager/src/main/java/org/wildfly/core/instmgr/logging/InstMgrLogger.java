/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.core.instmgr.logging;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

import java.io.IOException;


/**
 * Installation Manager logger.
 */
@SuppressWarnings("DefaultAnnotationParam")
@MessageLogger(projectCode = "WFLYIM", length = 4)
public interface InstMgrLogger extends BasicLogger {
    InstMgrLogger ROOT_LOGGER = Logger.getMessageLogger(InstMgrLogger.class, " org.wildfly.core.installationmanager");

    @Message(id = 1, value = "No known attribute %s")
    OperationFailedException unknownAttribute(String asString);

    @Message(id = 2, value = "Zip entry %s is outside of the target dir %s")
    IOException zipEntryOutsideOfTarget(String entry, String target);

    @Message(id = 3, value = "Unable to locate the root directory of the unloaded Zip File")
    Exception invalidZipEntry();

    @Message(id = 4, value = "There is an installation prepared and ready to be applied. The current prepared installation can be discarded by using the 'clean' operation.")
    OperationFailedException serverAlreadyPrepared();

    @Message(id = 5, value = "The %s operation is not supported")
    OperationFailedException unsupportedOperation(String asString);
}

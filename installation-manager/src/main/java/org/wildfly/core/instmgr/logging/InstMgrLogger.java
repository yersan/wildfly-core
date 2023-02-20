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
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

import java.io.IOException;

import static org.jboss.logging.Logger.Level.WARN;


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

    //
    @LogMessage(level = WARN)
    @Message(id = 6, value = "Invalid status change found for the artifact: \"%s\"")
    void unexpectedArtifactChange(String artifact);

    @LogMessage(level = WARN)
    @Message(id = 7, value = "Invalid status change found for the configuration change: \"%s\"")
    void unexpectedConfigurationChange(String channel);

    @Message(id = 8, value = "Channel name is mandatory")
    OperationFailedException missingChannelName();

    @Message(id = 9, value = "No repositories are defined in the '%s' channel.")
    OperationFailedException noRepositoriesDefinedForChannel(String channelName);

    @Message(id = 10, value = "The '%s' channel's repository does not have any defined URL.")
    OperationFailedException noRepositoryURLDefined(String channelName);

    @Message(id = 11, value = "The repository URL '%s' for '%s' channel is invalid.")
    OperationFailedException invalidRepositoryURLForChannel(String repoUrl, String channelName);

    @Message(id = 12, value = "The '%s' channel's repository does not have any defined ID")
    OperationFailedException noRepositoryIDDefined(String channelName);

    @Message(id = 13, value = "The GAV manifest '%s' for '%s' channel is invalid.")
    OperationFailedException invalidGAVManifestForChannel(String gav, String channelName);

    @Message(id = 14, value = "The URL manifest '%s' for '%s' channel is invalid.")
    OperationFailedException invalidURLManifestForChannel(String url, String channelName);
}

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

package org.wildfly.core.instmgr;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Tracks the status of the candidate by using the installation-manager.properties file and configures the values that are
 * passed to the installation-manager.sh/bat script to apply or revert an installation.
 */
class InstMgrCandidateStatus {
    private Path properties;
    public final String INST_MGR_STATUS_KEY = "INST_MGR_STATUS";
    public final String INST_MGR_SCRIPT_NAME_KEY = "INST_MGR_SCRIPT_NAME";
    public final String INST_MGR_ACTION_KEY = "INST_MGR_ACTION";

    public enum Status {ERROR, CLEAN, PREPARING, PREPARED}

    public void initialize(Path properties) {
        this.properties = properties.normalize().toAbsolutePath();
    }

    Status getStatus() throws IOException {
        try (FileInputStream in = new FileInputStream(properties.toString())) {
            final Properties prop = new Properties();
            prop.load(in);
            return Status.valueOf((String) prop.get(INST_MGR_STATUS_KEY));
        }
    }

    void begin() {
        setStatus(Status.PREPARING);
    }

    void reset() {
        setStatus(Status.CLEAN);
    }

    void setFailed() {
        setStatus(Status.ERROR);
    }

    void commit(String scriptName, String action) {
        setStatus(Status.PREPARED, scriptName, action);
    }

    private void setStatus(Status status) {
        setStatus(status, "", "");
    }

    private void setStatus(Status status, String scriptName, String action) {
        try (FileInputStream in = new FileInputStream(properties.toString())) {
            final Properties prop = new Properties();
            prop.load(in);
            in.close();

            try (FileOutputStream out = new FileOutputStream(properties.toString())) {
                prop.setProperty(INST_MGR_SCRIPT_NAME_KEY, scriptName);
                prop.setProperty(INST_MGR_ACTION_KEY, action);
                prop.setProperty(INST_MGR_STATUS_KEY, status.name());
                prop.store(out, null);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

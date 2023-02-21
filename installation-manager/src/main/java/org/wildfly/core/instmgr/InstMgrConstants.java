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

public interface InstMgrConstants {
    String CHANNEL = "channel";
    String CHANNELS = "channels";
    String CHANNEL_NAME = "name";
    String INSTALLATION_MANAGER = "installation-manager";
    String LIST_UPDATES_WORK_DIR = "work-dir";
    String LOCAL_CACHE = "local-cache";
    String MANIFEST = "manifest";
    String MANIFEST_GAV = "gav";
    String MANIFEST_URL = "url";
    String MAVEN_REPO_FILE = "maven-repo-file";
    String NO_RESOLVE_LOCAL_CACHE = "no-resolve-local-cache";
    String OFFLINE = "offline";
    String REPOSITORIES = "repositories";
    String REPOSITORY = "repository";
    String REPOSITORY_URL = "url";
    String REPOSITORY_ID = "id";
    String RETURN_CODE = "return-code";
    String REVISION = "revision";
    String TOOL_NAME = "installer";
    String UPDATES_RESULT = "updates";
    int RETURN_CODE_NO_UPDATES = 2;
    int RETURN_CODE_UPDATES_WITHOUT_WORK_DIR = 1;
    int RETURN_CODE_UPDATES_WITH_WORK_DIR = 0;
    String HASH = "hash";
    String TIMESTAMP = "timestamp";
    String TYPE = "type";
}

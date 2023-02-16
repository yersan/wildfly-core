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

import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;

class InstMgrResolver {

    static final ResourceDescriptionResolver RESOLVER = InstMgrResolver.getResourceDescriptionResolver();
    static final String KEY_REMOVED_ARTIFACT = "installation-manager.messages.removed-artifact";
    static final String KEY_INSTALLED_ARTIFACT = "installation-manager.messages.installed-artifact";
    static final String KEY_UPDATED_ARTIFACT = "installation-manager.messages.updated-artifact";
    static final String KEY_UPDATES_FOUND ="installation-manager.messages.updates-found";
    static final String KEY_NO_UPDATES_FOUND ="installation-manager.messages.no-updates-found";
    static final String KEY_REMOVED_CHANNEL = "installation-manager.messages.removed-channel";
    static final String KEY_REMOVED_CHANNEL_NAME = "installation-manager.messages.removed-channel.name";
    static final String KEY_REMOVED_CHANNEL_MANIFEST = "installation-manager.messages.removed-channel.manifest";
    static final String KEY_REMOVED_CHANNEL_REPOSITORY = "installation-manager.messages.removed-channel.repository";
    static final String KEY_ADDED_CHANNEL = "installation-manager.messages.added-channel";
    static final String KEY_ADDED_CHANNEL_NAME = "installation-manager.messages.added-channel.name";
    static final String KEY_ADDED_CHANNEL_MANIFEST = "installation-manager.messages.added-channel";
    static final String KEY_ADDED_CHANNEL_REPOSITORY = "installation-manager.messages.added-channel";
    static final String KEY_UPDATED_CHANNEL = "installation-manager.messages.updated-channel";
    static final String KEY_UPDATED_CHANNEL_NAME = "installation-manager.messages.updated-channel.name";
    static final String KEY_UPDATED_CHANNEL_MANIFEST = "installation-manager.messages.updated-channel.manifest";
    static final String KEY_UPDATED_CHANNEL_REPOSITORY = "installation-manager.messages.updated-channel.repositories";
    static final String KEY_CANDIDATE_SERVER_PREPARED_AT = "installation-manager.messages.candidate-server-prepared-at";
    static final String KEY_CLONE_EXPORT_RESULT = "installation-manager.messages.clone-export.result";


    private static ResourceDescriptionResolver getResourceDescriptionResolver(final String... keyPrefixes) {
        StringBuilder sb = new StringBuilder(InstMgrConstants.INSTALLATION_MANAGER);
        if (keyPrefixes != null) {
            for (String current : keyPrefixes) {
                sb.append(".").append(current);
            }
        }

        return new StandardResourceDescriptionResolver(sb.toString(), InstMgrResolver.class.getPackage().getName() + ".LocalDescriptions", InstMgrResolver.class.getClassLoader(), true, false);
    }

    static String getString(String key) {
        return RESOLVER.getResourceBundle(null)
                .getString(key);
    }
}
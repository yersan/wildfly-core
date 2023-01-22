package org.wildfly.core.instmgr;

import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;

class InstMgrResolver {

    static final ResourceDescriptionResolver RESOLVER = InstMgrResolver.getResourceDescriptionResolver();
    static final String KEY_REMOVED_ARTIFACT = "installation-manager.messages.removed-artifact";
    static final String KEY_INSTALLED_ARTIFACT = "installation-manager.messages.installed-artifact";
    static final String KEY_UPDATED_ARTIFACT = "installation-manager.messages.updated-artifact";
    static final String KEY_NO_CHANGES_FOUND ="installation-manager.messages.no-changes-found";

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

package org.wildfly.core.instmgr.spi;

import org.wildfly.installationmanager.ArtifactChange;
import org.wildfly.installationmanager.Channel;
import org.wildfly.installationmanager.HistoryResult;
import org.wildfly.installationmanager.InstallationChanges;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.Repository;
import org.wildfly.installationmanager.spi.InstallationManager;

import java.nio.file.Path;
import java.util.AbstractList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ProsperoInstallationManager implements InstallationManager {


    public ProsperoInstallationManager(Path installationDir, MavenOptions mavenOptions) throws Exception {

    }

    @Override
    public List<HistoryResult> history() throws Exception {
        return new AbstractList<HistoryResult>() {
            @Override
            public HistoryResult get(int index) {
                return null;
            }

            @Override
            public int size() {
                return 0;
            }
        };
    }

    @Override
    public InstallationChanges revisionDetails(String revision) throws Exception {
        return new InstallationChanges(Collections.EMPTY_LIST, Collections.EMPTY_LIST);
    }

    @Override
    public void prepareRevert(String revision, Path targetDir, List<Repository> repositories) throws Exception {

    }

    @Override
    public void prepareUpdate(Path targetDir, List<Repository> repositories) throws Exception {

    }

    @Override
    public List<ArtifactChange> findUpdates(List<Repository> repositories) throws Exception {
        return Collections.emptyList();
    }

    @Override
    public Collection<Channel> listChannels() throws Exception {
        return Collections.emptyList();
    }

    @Override
    public void removeChannel(String channelName) throws Exception {

    }

    @Override
    public void addChannel(Channel channel) throws Exception {

    }

    @Override
    public void changeChannel(String channelName, Channel newChannel) throws Exception {

    }

    @Override
    public Path createSnapshot(Path targetPath) throws Exception {
        return null;
    }
}

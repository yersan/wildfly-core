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

package org.wildfly.core.instmgr.spi;

import org.wildfly.installationmanager.ArtifactChange;
import org.wildfly.installationmanager.Channel;
import org.wildfly.installationmanager.ChannelChange;
import org.wildfly.installationmanager.HistoryResult;
import org.wildfly.installationmanager.InstallationChanges;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.OperationNotAvailableException;
import org.wildfly.installationmanager.Repository;
import org.wildfly.installationmanager.spi.InstallationManager;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Instant;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class TestInstallationManager implements InstallationManager {
    public static MavenOptions mavenOptions;
    public static Path installationDir;
    public static List<Channel> lstChannels;

    public static InstallationChanges installationChanges;
    public static HashMap<String, HistoryResult> history;

    public static void initData() {
        installationDir = null;
        mavenOptions = null;

        // Channels Sample Data
        lstChannels = new ArrayList<>();

        List<Repository> repoList = new ArrayList<>();
        repoList.add(new Repository("id0", "http://localhost"));
        repoList.add(new Repository("id1", "file://dummy"));
        lstChannels.add(new Channel("channel-test-0", repoList, "org.test.groupid:org.test.artifactid:1.0.0.Final"));

        repoList = new ArrayList<>();
        repoList.add(new Repository("id1", "file://dummy"));
        try {
            lstChannels.add(new Channel("channel-test-1", repoList, new URL("file://dummy")));
        } catch (MalformedURLException e) {
            // ignored
        }

        repoList = new ArrayList<>();
        repoList.add(new Repository("id0", "http://localhost"));
        repoList.add(new Repository("id2", "file://dummy"));
        lstChannels.add(new Channel("channel-test-2", repoList));


        // Changes Sample Data
        List<ArtifactChange> artifactChanges = new ArrayList<>();
        ArtifactChange installed = new ArtifactChange("1.0.0.Final", "1.0.1.Final", "org.test.groupid1:org.test.artifact1", ArtifactChange.Status.INSTALLED);
        ArtifactChange removed = new ArtifactChange("1.0.0.Final", "1.0.1.Final", "org.test.groupid1:org.test.artifact1", ArtifactChange.Status.REMOVED);
        ArtifactChange updated = new ArtifactChange("1.0.0.Final", "1.0.1.Final", "org.test.groupid1:org.test.artifact1", ArtifactChange.Status.UPDATED);
        artifactChanges.add(installed);
        artifactChanges.add(removed);
        artifactChanges.add(updated);

        List<ChannelChange> channelChanges  = new ArrayList<>();
        List<Repository> channelChangeBaseRepositories = new ArrayList<>();
        channelChangeBaseRepositories.add(new Repository("id0", "http://channelchange.com"));
        channelChangeBaseRepositories.add(new Repository("id1", "file://channelchange"));
        Channel channelChangeBase = new Channel("channel-test-0", channelChangeBaseRepositories, "org.channelchange.groupid:org.channelchange.artifactid:1.0.0.Final");
        Channel channelChangeModified = new Channel("channel-test-0", channelChangeBaseRepositories, "org.channelchange.groupid:org.channelchange.artifactid:1.0.1.Final");

        ChannelChange cChangeAdded = new ChannelChange(null, channelChangeBase, ChannelChange.Status.ADDED);
        ChannelChange cChangeRemoved = new ChannelChange(channelChangeBase, null, ChannelChange.Status.REMOVED);
        ChannelChange cChangeModified = new ChannelChange(channelChangeBase, channelChangeModified, ChannelChange.Status.MODIFIED);
        channelChanges.add(cChangeAdded);
        channelChanges.add(cChangeRemoved);
        channelChanges.add(cChangeModified);
        installationChanges = new InstallationChanges(artifactChanges, channelChanges);


        // History sample data
        history = new HashMap<>();
        history.put("update", new HistoryResult("update", Instant.now(), "update" ));
        history.put("install", new HistoryResult("install", Instant.now(), "install" ));
        history.put("rollback", new HistoryResult("rollback", Instant.now(), "rollback" ));
        history.put("config_change", new HistoryResult("config_change", Instant.now(), "config_change" ));
    }

    public TestInstallationManager(Path installationDir, MavenOptions mavenOptions) throws Exception {
        this.installationDir = installationDir;
        this.mavenOptions = mavenOptions;
    }

    @Override
    public List<HistoryResult> history() throws Exception {
        return new ArrayList<>(history.values());
    }

    @Override
    public InstallationChanges revisionDetails(String revision) throws Exception {
       return installationChanges;
    }

    @Override
    public void prepareRevert(String revision, Path targetDir, List<Repository> repositories) throws Exception {

    }

    @Override
    public boolean prepareUpdate(Path targetDir, List<Repository> repositories) throws Exception {
        return false;
    }

    @Override
    public List<ArtifactChange> findUpdates(List<Repository> repositories) throws Exception {
        return Collections.emptyList();
    }

    @Override
    public Collection<Channel> listChannels() throws Exception {
        return lstChannels;
    }

    @Override
    public void removeChannel(String channelName) throws Exception {
        for (Iterator<Channel> it = lstChannels.iterator(); it.hasNext();){
            Channel c = it.next();
            if (c.getName().equals(channelName)) {
                it.remove();
            }
        }
    }

    @Override
    public void addChannel(Channel channel) throws Exception {
        lstChannels.add(channel);
    }

    @Override
    public void changeChannel(String channelName, Channel newChannel) throws Exception {
        for (Iterator<Channel> it = lstChannels.iterator(); it.hasNext();){
            Channel c = it.next();
            if (c.getName().equals(newChannel.getName())) {
                it.remove();
            }
        }
        lstChannels.add(newChannel);
    }

    @Override
    public Path createSnapshot(Path targetPath) throws Exception {
        if (targetPath.toString().startsWith(".zip")) {
            return targetPath;
        }
        return targetPath.resolve("generated.zip");
    }

    @Override
    public String generateApplyUpdateCommand(Path candidatePath) throws OperationNotAvailableException {
        return null;
    }

    @Override
    public String generateApplyRevertCommand(Path candidatePath) throws OperationNotAvailableException {
        return null;
    }
}

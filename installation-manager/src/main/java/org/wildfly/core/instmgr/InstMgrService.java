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

import org.jboss.as.controller.services.path.PathManager;
import org.jboss.logging.Logger;
import org.jboss.msc.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * This is the main service used by the installation manager management operation handlers.
 */
class InstMgrService implements Service {
    private final static Logger LOG = Logger.getLogger(InstMgrService.class);
    private final Supplier<PathManager> pathManagerSupplier;
    private PathManager pathManager;
    private AtomicBoolean started = new AtomicBoolean(false);
    private Path homeDir = null;
    private Path properties = null;
    private Path prepareServerDir = null;
    private HashMap<String, Path> tempDirs = new HashMap<>();
    private InstMgrCandidateStatus candidateStatus;

    InstMgrService(Supplier<PathManager> pathManagerSupplier) {
        this.pathManagerSupplier = pathManagerSupplier;
        this.candidateStatus = new InstMgrCandidateStatus();
    }

    @Override
    public void start(StartContext startContext) throws StartException {
        this.pathManager = pathManagerSupplier.get();
        this.homeDir = Path.of(this.pathManager.getPathEntry("jboss.home.dir").resolvePath());
        this.properties = homeDir.resolve("bin").resolve("installation-manager.properties");
        Path workDir = Paths.get(pathManager.getPathEntry("jboss.controller.temp.dir").resolvePath())
                .resolve("installation-manager");
        this.prepareServerDir = workDir.resolve("prepared-server");
        this.candidateStatus.initialize(properties);
        try {
            if (candidateStatus.getStatus() == InstMgrCandidateStatus.Status.PREPARING) {
                candidateStatus.setFailed();
            }
            //Ensure work dir is available
            Files.createDirectories(workDir);
        } catch (IOException e) {
            throw new StartException(e);
        }
        started.set(true);
    }

    @Override
    public void stop(StopContext context) {
        this.pathManager = null;
        try {
            deleteTempDirs();
        } catch (IOException e) {
            // ignored, maybe log it?
        }
        started.set(false);
    }

    Path createTempDir(String workDirPrefix) throws IOException {
        Path tempDirectory = Files.createTempDirectory(workDirPrefix);
        this.tempDirs.put(tempDirectory.getFileName().toString(), tempDirectory);

        return tempDirectory;
    }

    Path getTempDirByName(String workDirName) {
        return this.tempDirs.get(workDirName);
    }

    void deleteTempDirs() throws IOException {
        for (Iterator<Map.Entry<String, Path>> it = tempDirs.entrySet().iterator(); it.hasNext(); ) {
            Path workDir = it.next().getValue();
            Files.walk(workDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            it.remove();
        }
    }

    void deleteTempDir(Path tempDirPath) throws IOException {
        if (tempDirPath == null) {
            return;
        }

        deleteTempDir(tempDirPath.getFileName().toString());
    }

    void deleteTempDir(String tempDirName) throws IOException {
        if (tempDirName == null) {
            return;
        }

        Path dirToClean = this.tempDirs.get(tempDirName);
        tempDirs.remove(tempDirName);
        if (dirToClean != null && dirToClean.toFile().exists()) {
            Files.walk(dirToClean)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    private void checkStarted() throws IllegalStateException {
        if (!started.get()) throw new IllegalStateException();
    }

    Path getHomeDir() throws IllegalStateException {
        checkStarted();
        return homeDir;
    }

    Path getPreparedServerDir() throws IllegalStateException {
        checkStarted();
        return prepareServerDir;
    }

    boolean canPrepareServer() {
        try {
            return this.candidateStatus.getStatus() == InstMgrCandidateStatus.Status.CLEAN;
        } catch (IOException e) {
            LOG.debug("Cannot load the prepared server status from a properties file", e);
            return false;
        }
    }

    void beginCandidateServer() {
        this.candidateStatus.begin();
    }

    void commitCandidateServer(String scriptName, String command) {
        this.candidateStatus.commit(scriptName, command);
    }

    void resetCandidateStatus() {
        this.candidateStatus.reset();
    }
}

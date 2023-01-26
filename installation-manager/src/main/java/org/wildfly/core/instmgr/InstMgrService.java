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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

class InstMgrService implements Service {
    private final Supplier<PathManager> pathManagerSupplier;
    private final Supplier<ExecutorService> executorServiceSupplier;
    private PathManager pathManager;
    private ExecutorService executorService;
    private AtomicBoolean started = new AtomicBoolean(false);
    private static final AtomicBoolean serverPrepared = new AtomicBoolean(false);
    private Path workDir = null;

    private HashMap<String, Path> trackedWorkDirs = new HashMap<>();

    InstMgrService(Supplier<PathManager> pathManagerSupplier, Supplier<ExecutorService> executorServiceSupplier) {
        this.pathManagerSupplier = pathManagerSupplier;
        this.executorServiceSupplier = executorServiceSupplier;
    }

    @Override
    public void start(StartContext startContext) throws StartException {
        this.pathManager = pathManagerSupplier.get();
        this.executorService = executorServiceSupplier.get();

        workDir = Paths.get(pathManager.getPathEntry("jboss.controller.temp.dir").resolvePath())
                .resolve("installation-manager");
        try {
            //Ensure work dir is available
            Files.createDirectories(workDir);
            serverPrepared.set(workDir.resolve("prepared-server").toFile().exists());
        } catch (IOException e) {
            throw new StartException(e);
        }
        started.set(true);
    }

    @Override
    public void stop(StopContext context) {
        this.pathManager = null;
        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    cleanTrackedWorkDirs();
                } catch (IOException e) {
                    // ignored, maybe log it?
                } finally {
                    trackedWorkDirs = null;
                    context.complete();
                }
            }
        };
        try {
            executorService.execute(r);
        } catch (RejectedExecutionException e) {
            r.run();
        } finally {
            context.asynchronous();
        }
        started.set(false);
    }

    Path createWorkDir(String workDirPrefix) throws IOException {
        Path tempDirectory = Files.createTempDirectory(workDirPrefix);
        this.trackedWorkDirs.put(tempDirectory.getFileName().toString(), tempDirectory);

        return tempDirectory;
    }

    Path getTrackedWorkDirByName(String workDirName) {
        return this.trackedWorkDirs.get(workDirName);
    }

    void cleanTrackedWorkDirs() throws IOException {
        for (Path workDir : trackedWorkDirs.values()) {
            Files.walk(workDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    void cleanTrackedWorkDir(String workDirName) throws IOException {
        if (workDirName == null) {
            return;
        }

        Path dirToClean = this.trackedWorkDirs.get(workDirName);
        trackedWorkDirs.remove(workDirName);
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

    private PathManager getPathManager() throws IllegalStateException {
        checkStarted();
        return this.pathManager;
    }

    Path getHomeDir() throws IllegalStateException {
        checkStarted();
        return Path.of(getPathManager().getPathEntry("jboss.home.dir").resolvePath());
    }

    Path getPreparedServerDir() throws IllegalStateException {
        return workDir.resolve("prepared-server");
    }

    Path getApplyUpdatesCliScript() throws IllegalStateException {
        // This must be in sync with the path specified in JBOSS_HOME/bin/installation-manager/installation-manager.sh
        return getHomeDir().resolve("bin").resolve("installation-manager").resolve("installation-manager-lib.sh");
    }

    void setServerPrepared(boolean value) {
        serverPrepared.set(value);
    }

    boolean isServerPrepared() {
        return serverPrepared.get();
    }
}

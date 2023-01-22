package org.wildfly.core.instmgr;

import org.jboss.as.controller.services.path.PathManager;
import org.jboss.msc.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

class InstMgrService implements Service {
    private final Supplier<PathManager> pathManagerSupplier;
    private PathManager pathManager;
    private AtomicBoolean started = new AtomicBoolean(false);
    private static final AtomicBoolean serverPrepared = new AtomicBoolean(false);
    private Path workDir = null;

    public InstMgrService(Supplier<PathManager> pathManagerSupplier) {
        this.pathManagerSupplier = pathManagerSupplier;
    }

    @Override
    public void start(StartContext startContext) throws StartException {
        this.pathManager = pathManagerSupplier.get();
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
    public void stop(StopContext stopContext) {
        this.pathManager = null;
        started.set(false);
    }

    private void checkStarted() throws IllegalStateException {
        if (!started.get()) throw new IllegalStateException();
    }

    private PathManager getPathManager() throws IllegalStateException {
        checkStarted();
        return this.pathManager;
    }

    public Path getHomeDir() throws IllegalStateException {
        checkStarted();
        return Path.of(getPathManager().getPathEntry("jboss.home.dir").resolvePath());
    }

    public Path getWorkdir() throws IllegalStateException {
        checkStarted();
        return workDir;
    }

    public Path getPreparedServerDir() throws IllegalStateException {
        return getWorkdir().resolve("prepared-server");
    }

    public Path getApplyUpdatesCliScript() throws IllegalStateException {
        // This must be in sync with the path specified in JBOSS_HOME/bin/installation-manager/installation-manager.sh
        return getHomeDir().resolve("bin").resolve("installation-manager").resolve("installation-manager-lib.sh");
    }

    public void setServerPrepared(boolean value) {
        serverPrepared.set(value);
    }

    public boolean isServerPrepared() {
        return serverPrepared.get();
    }
}

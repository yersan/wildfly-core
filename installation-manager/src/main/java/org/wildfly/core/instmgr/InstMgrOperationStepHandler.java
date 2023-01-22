package org.wildfly.core.instmgr;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.instmgr.logging.InstMgrLogger;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;

abstract class InstMgrOperationStepHandler implements OperationStepHandler {
    private final InstMgrService imService;
    private final InstallationManagerFactory imf;

    InstMgrOperationStepHandler(InstMgrService imService, InstallationManagerFactory imf) {
        this.imService = imService;
        this.imf = imf;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        if (requiresRuntime(context)) {
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    // Ensure the resource exist
                    context.readResource(PathAddress.EMPTY_ADDRESS, false);
                    executeRuntimeStep(context, operation);
                }
            }, OperationContext.Stage.RUNTIME);
        }
    }

    protected boolean requiresRuntime(OperationContext context) {
        ProcessType processType = context.getProcessType();

        if (processType.isServer()) {
            return context.isNormalServer();
        } else if (processType.isHostController()) {
            PathAddress currentAddress = context.getCurrentAddress();
            return currentAddress.size() >= 2 && currentAddress.getElement(0).getKey().equals(HOST) && currentAddress.getElement(1).getKey().equals(CORE_SERVICE);
        }
        return false;
    }

    protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
        this.executeRuntimeStep(context, operation, imService, imf);
    }

    abstract void executeRuntimeStep(OperationContext context, ModelNode operation, InstMgrService imService, InstallationManagerFactory imf) throws OperationFailedException;

    protected void deleteDirIfExits(Path dir, boolean skipRootDir) throws IOException {
        if (dir != null && dir.toFile().exists()) {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .filter(f -> !skipRootDir || skipRootDir && !f.equals(dir))
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    protected void deleteDirIfExits(Path dir) throws IOException {
        this.deleteDirIfExits(dir, false);
    }

    protected <T extends AttributeDefinition> ModelNode resolveAttribute(OperationContext context, ModelNode operation, T attr) throws OperationFailedException {
        return attr.resolveValue(context, attr.validateOperation(operation));
    }

    /**
     * unpack...
     *
     * @param zip      the zip
     * @param patchDir the patch dir
     * @throws IOException
     */
    protected static void unzip(final File zip, final File patchDir) throws IOException {
        try (final ZipFile zipFile = new ZipFile(zip)) {
            unzip(zipFile, patchDir);
        }
    }

    /**
     * unpack...
     *
     * @param zip      the zip
     * @param patchDir the patch dir
     * @throws IOException
     */
    private static void unzip(final ZipFile zip, final File patchDir) throws IOException {
        final Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            final ZipEntry entry = entries.nextElement();
            final String name = entry.getName();
            final File current = new File(patchDir, name);
            if (!current.getCanonicalFile().toPath().startsWith(patchDir.getCanonicalFile().toPath())) {
                throw InstMgrLogger.ROOT_LOGGER.zipEntryOutsideOfTarget(current.getAbsolutePath(), patchDir.getAbsolutePath());
            }
            if (!entry.isDirectory()) {
                if (!current.getParentFile().exists()) {
                    current.getParentFile().mkdirs();
                }
                try (final InputStream eis = zip.getInputStream(entry)) {
                    Files.copy(eis, current.toPath());
                }
            }
        }
    }
}

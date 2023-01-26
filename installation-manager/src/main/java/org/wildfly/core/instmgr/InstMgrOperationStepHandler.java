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


    protected static void deleteDirIfExits(Path dir, boolean skipRootDir) throws IOException {
        if (dir != null && dir.toFile().exists()) {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .filter(f -> !skipRootDir || skipRootDir && !f.equals(dir))
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    protected static void deleteDirIfExits(Path dir) throws IOException {
        deleteDirIfExits(dir, false);
    }

    protected static <T extends AttributeDefinition> ModelNode resolveAttribute(OperationContext context, ModelNode operation, T attr) throws OperationFailedException {
        return attr.resolveValue(context, attr.validateOperation(operation));
    }

    protected static void deleteOnShutDownHook(final Path workDir) {
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    deleteDirIfExits(workDir);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        thread.setDaemon(true);
        Runtime.getRuntime().addShutdownHook(thread);
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

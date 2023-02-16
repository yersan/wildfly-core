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

import org.jboss.as.controller.OperationStepHandler;
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

/**
 * Base class for installation-manager operation handlers.
 */
abstract class InstMgrOperationStepHandler implements OperationStepHandler {
    protected final InstMgrService imService;
    protected final InstallationManagerFactory imf;

    InstMgrOperationStepHandler(InstMgrService imService, InstallationManagerFactory imf) {
        this.imService = imService;
        this.imf = imf;
    }

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

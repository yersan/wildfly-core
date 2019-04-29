/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.server.moduleservice;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarFile;

import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.ResourceLoaders;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.vfs.VFSUtils;

/**
 * Service that manages the module spec for external modules (i.e. modules that reside outside of the application server).
 *
 * @author Stuart Douglas
 *
 */
public class ExternalModuleSpecService implements Service<ModuleDefinition> {

    private final ModuleIdentifier moduleIdentifier;

    private final File file;

    private volatile ModuleDefinition moduleDefinition;

    private List<JarFile> jarFiles;

    public ExternalModuleSpecService(ModuleIdentifier moduleIdentifier, File file) {
        this.moduleIdentifier = moduleIdentifier;
        this.file = file;
        this.jarFiles = new ArrayList<>();
    }

    @Override
    public synchronized void start(StartContext context) throws StartException {
        final ModuleSpec.Builder specBuilder;
        try {
            if (! file.isDirectory()) {
                this.jarFiles.add(new JarFile(file));
                specBuilder = ModuleSpec.build(moduleIdentifier.toString());
                addResourceRoot(specBuilder, jarFiles.get(0));
            } else {
                specBuilder = ModuleSpec.build(moduleIdentifier.toString());
                addFolderResourceRoot(specBuilder, file.toPath());
                final File[] jars = file.listFiles(file -> file.getName().endsWith(".jar") && !file.isDirectory());
                if (jars != null) {
                    Arrays.sort(jars, Comparator.comparing(File::getName).thenComparing(File::length));
                    for (File jar : jars) {
                        JarFile jarFile = new JarFile(jar);
                        this.jarFiles.add(jarFile);
                        addResourceRoot(specBuilder, jarFile);
                    }
                }
            }
        } catch (IOException e) {
            throw new StartException(e);
        }

        //TODO: We need some way of configuring module dependencies for external archives
        ModuleIdentifier javaee = ModuleIdentifier.create("javaee.api");
        specBuilder.addDependency(DependencySpec.createModuleDependencySpec(javaee));
        specBuilder.addDependency(DependencySpec.createLocalDependencySpec());
        // TODO: external resource need some kind of dependency mechanism
        ModuleSpec moduleSpec = specBuilder.create();
        this.moduleDefinition = new ModuleDefinition(moduleIdentifier, Collections.<ModuleDependency>emptySet(), moduleSpec);


        ServiceModuleLoader.installModuleResolvedService(context.getChildTarget(), moduleIdentifier);

    }

    @Override
    public synchronized void stop(StopContext context) {
        for (JarFile jarFile : jarFiles) {
            VFSUtils.safeClose(jarFile);
        }
        jarFiles.clear();
        jarFiles = null;
        moduleDefinition = null;
    }

    @Override
    public ModuleDefinition getValue() throws IllegalStateException, IllegalArgumentException {
        return moduleDefinition;
    }

    private static void addResourceRoot(final ModuleSpec.Builder specBuilder, final JarFile file) {
        specBuilder.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(ResourceLoaders.createJarResourceLoader(
                    file.getName(), file)));
    }

    private static void addFolderResourceRoot(final ModuleSpec.Builder specBuilder, Path path) {
        specBuilder.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(ResourceLoaders.createPathResourceLoader(path)));
    }
}

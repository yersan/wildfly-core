/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment.module.descriptor;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jboss.as.server.DeployerChainAddHandler;
import org.jboss.as.server.ServerService;
import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.DeploymentUtils;
import org.jboss.as.server.deployment.Phase;
import org.jboss.as.server.deployment.SubDeploymentMarker;
import org.jboss.as.server.deployment.annotation.ResourceRootIndexer;
import org.jboss.as.server.deployment.jbossallxml.JBossAllXmlParserRegisteringProcessor;
import org.jboss.as.server.deployment.module.AdditionalModuleSpecification;
import org.jboss.as.server.deployment.module.ModuleAliasChecker;
import org.jboss.as.server.deployment.module.ModuleAliasChecker.MessageContext;
import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.as.server.deployment.module.ModuleRootMarker;
import org.jboss.as.server.deployment.module.ModuleSpecification;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.moduleservice.ServiceModuleLoader;
import org.jboss.modules.ModuleLoader;
import org.jboss.staxmapper.XMLMapper;
import org.jboss.vfs.VirtualFile;

/**
 * Parses <code>jboss-deployment-structure.xml</code>, and merges the result with the deployment.
 * <p/>
 * <code>jboss-deployment-structure.xml</code> is only parsed for top level deployments. It allows configuration of the following for
 * deployments and sub deployments:
 * <ul>
 * <li>Additional dependencies</li>
 * <li>Additional resource roots</li>
 * <li>{@link java.lang.instrument.ClassFileTransformer}s that will be applied at classloading</li>
 * <li>Child first behaviour</li>
 * </ul>
 * <p/>
 * It also allows for the use to add additional modules, using a syntax similar to that used in module xml files.
 *
 * @author Stuart Douglas
 * @author Marius Bogoevici
 */
public class DeploymentStructureDescriptorParser implements DeploymentUnitProcessor {

    public static final String[] DEPLOYMENT_STRUCTURE_DESCRIPTOR_LOCATIONS = {
            "META-INF/jboss-deployment-structure.xml",
            "WEB-INF/jboss-deployment-structure.xml"};

    private static final AttachmentKey<ParseResult> RESULT_ATTACHMENT_KEY = AttachmentKey.create(ParseResult.class);

    public static void registerJBossXMLParsers() {
        DeployerChainAddHandler.addDeploymentProcessor(ServerService.SERVER_NAME, Phase.STRUCTURE, Phase.STRUCTURE_REGISTER_JBOSS_ALL_STRUCTURE_1_0, new JBossAllXmlParserRegisteringProcessor<ParseResult>(ROOT_1_0, RESULT_ATTACHMENT_KEY, JBossDeploymentStructureParser10.JBOSS_ALL_XML_PARSER));
        DeployerChainAddHandler.addDeploymentProcessor(ServerService.SERVER_NAME, Phase.STRUCTURE, Phase.STRUCTURE_REGISTER_JBOSS_ALL_STRUCTURE_1_1, new JBossAllXmlParserRegisteringProcessor<ParseResult>(ROOT_1_1, RESULT_ATTACHMENT_KEY, JBossDeploymentStructureParser11.JBOSS_ALL_XML_PARSER));
        DeployerChainAddHandler.addDeploymentProcessor(ServerService.SERVER_NAME, Phase.STRUCTURE, Phase.STRUCTURE_REGISTER_JBOSS_ALL_STRUCTURE_1_2, new JBossAllXmlParserRegisteringProcessor<ParseResult>(ROOT_1_2, RESULT_ATTACHMENT_KEY, JBossDeploymentStructureParser12.JBOSS_ALL_XML_PARSER));
        DeployerChainAddHandler.addDeploymentProcessor(ServerService.SERVER_NAME, Phase.STRUCTURE, Phase.STRUCTURE_REGISTER_JBOSS_ALL_STRUCTURE_1_3, new JBossAllXmlParserRegisteringProcessor<ParseResult>(ROOT_1_3, RESULT_ATTACHMENT_KEY, JBossDeploymentStructureParser13.JBOSS_ALL_XML_PARSER));
    }


    private static final QName ROOT_1_0 = new QName(JBossDeploymentStructureParser10.NAMESPACE_1_0, "jboss-deployment-structure");
    private static final QName ROOT_1_1 = new QName(JBossDeploymentStructureParser11.NAMESPACE_1_1, "jboss-deployment-structure");
    private static final QName ROOT_1_2 = new QName(JBossDeploymentStructureParser12.NAMESPACE_1_2, "jboss-deployment-structure");
    private static final QName ROOT_1_3 = new QName(JBossDeploymentStructureParser13.NAMESPACE_1_3, "jboss-deployment-structure");
    private static final QName ROOT_NO_NAMESPACE = new QName("jboss-deployment-structure");


    private static final XMLInputFactory INPUT_FACTORY = XMLInputFactory.newInstance();

    private final AttachmentKey<ModuleStructureSpec> SUB_DEPLOYMENT_STRUCTURE = AttachmentKey.create(ModuleStructureSpec.class);

    private final XMLMapper mapper;

    public DeploymentStructureDescriptorParser() {
        mapper = XMLMapper.Factory.create();
        mapper.registerRootElement(ROOT_1_0, JBossDeploymentStructureParser10.INSTANCE);
        mapper.registerRootElement(ROOT_1_1, JBossDeploymentStructureParser11.INSTANCE);
        mapper.registerRootElement(ROOT_1_2, JBossDeploymentStructureParser12.INSTANCE);
        mapper.registerRootElement(ROOT_1_3, JBossDeploymentStructureParser13.INSTANCE);
        mapper.registerRootElement(ROOT_NO_NAMESPACE, JBossDeploymentStructureParser13.INSTANCE);
    }

    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ResourceRoot deploymentRoot = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);
        final ServiceModuleLoader moduleLoader = deploymentUnit.getAttachment(Attachments.SERVICE_MODULE_LOADER);

        if (deploymentUnit.getParent() != null) {
            //if the parent has already attached parsed data for this sub deployment we need to process it
            if (deploymentRoot.hasAttachment(SUB_DEPLOYMENT_STRUCTURE)) {
                final ModuleSpecification subModuleSpec = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);
                Map<String, AdditionalModuleSpecification> additionalModules = new HashMap<>();
                final List<AdditionalModuleSpecification> additionalModuleList = deploymentUnit.getParent().getAttachmentList(Attachments.ADDITIONAL_MODULES);
                // Must synchronize on list as subdeployments executing Phase.STRUCTURE may be concurrently modifying it
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (additionalModuleList) {
                    for (AdditionalModuleSpecification i : additionalModuleList) {
                        additionalModules.put(i.getModuleName(), i);
                    }
                }
                handleDeployment(phaseContext, deploymentUnit, subModuleSpec, deploymentRoot.getAttachment(SUB_DEPLOYMENT_STRUCTURE), additionalModules);
            }
        }

        VirtualFile deploymentFile = null;
        for (final String loc : DEPLOYMENT_STRUCTURE_DESCRIPTOR_LOCATIONS) {
            final VirtualFile file = deploymentRoot.getRoot().getChild(loc);
            if (file.exists()) {
                deploymentFile = file;
                break;
            }
        }
        ParseResult result = deploymentUnit.getAttachment(RESULT_ATTACHMENT_KEY);
        if (deploymentFile == null && result == null) {
            return;
        }
        if (deploymentUnit.getParent() != null) {
            if(deploymentFile != null) {
                ServerLogger.DEPLOYMENT_LOGGER.jbossDeploymentStructureIgnored(deploymentFile.getPathName());
            }
            if(result != null) {
                ServerLogger.DEPLOYMENT_LOGGER.jbossDeploymentStructureNamespaceIgnored(deploymentUnit.getName());
            }
            return;
        }

        try {
            if(deploymentFile != null) {
                result = parse(deploymentFile.getPhysicalFile(), deploymentUnit, moduleLoader);
            }
            // handle additional modules
            Map<String, AdditionalModuleSpecification> additionalModules = new HashMap<>();
            for (final ModuleStructureSpec additionalModule : result.getAdditionalModules()) {
                for (final String identifier : additionalModule.getAnnotationModules()) {
                    //additional modules don't support annotation imports
                    ServerLogger.DEPLOYMENT_LOGGER.annotationImportIgnored(identifier, additionalModule.getModuleName());
                }
                //log a warning if the resource root is wrong
                final List<ResourceRoot> additionalModuleResourceRoots = new ArrayList<>(additionalModule.getResourceRoots());
                final ListIterator<ResourceRoot> itr = additionalModuleResourceRoots.listIterator();
                while (itr.hasNext()) {
                    final ResourceRoot resourceRoot = itr.next();
                    if(!resourceRoot.getRoot().exists()) {
                        ServerLogger.DEPLOYMENT_LOGGER.additionalResourceRootDoesNotExist(resourceRoot.getRoot().getPathName());
                        itr.remove();
                    }
                }
                final AdditionalModuleSpecification additional = new AdditionalModuleSpecification(additionalModule.getModuleName(), additionalModuleResourceRoots);
                for (String alias : additionalModule.getAliasesList()) {
                    additional.addModuleAlias(alias);
                }
                additional.addSystemDependencies(additionalModule.getModuleDependencies());
                additionalModules.put(additional.getModuleName(), additional);
                deploymentUnit.addToAttachmentList(Attachments.ADDITIONAL_MODULES, additional);
                for (final ResourceRoot root : additionalModuleResourceRoots) {
                    ResourceRootIndexer.indexResourceRoot(root);
                }
            }

            final ModuleSpecification moduleSpec = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);
            if (result.getEarSubDeploymentsIsolated() != null) {
                // set the ear subdeployment isolation value overridden via the jboss-deployment-structure.xml
                moduleSpec.setSubDeploymentModulesIsolated(result.getEarSubDeploymentsIsolated());
            }
            if(result.getEarExclusionsCascadedToSubDeployments() != null) {
                // set the ear cascade exclusions to sub-deployments flag as configured in jboss-deployment-structure.xml
                moduleSpec.setExclusionsCascadedToSubDeployments(result.getEarExclusionsCascadedToSubDeployments());
            }
            // handle the root deployment
            final ModuleStructureSpec rootDeploymentSpecification = result.getRootDeploymentSpecification();
            if (rootDeploymentSpecification != null) {
                handleDeployment(phaseContext, deploymentUnit, moduleSpec, rootDeploymentSpecification, additionalModules);
            }
            // handle sub deployments
            final Map<String, ResourceRoot> subDeploymentMap = new HashMap<>();
            final List<ResourceRoot> resourceRoots = deploymentUnit.getAttachmentList(Attachments.RESOURCE_ROOTS);
            for (final ResourceRoot root : resourceRoots) {
                if (SubDeploymentMarker.isSubDeployment(root)) {
                    subDeploymentMap.put(root.getRoot().getPathNameRelativeTo(deploymentRoot.getRoot()), root);
                }
            }

            for (final Entry<String, ModuleStructureSpec> entry : result.getSubDeploymentSpecifications().entrySet()) {
                final String path = entry.getKey();
                final ModuleStructureSpec spec = entry.getValue();
                if (!subDeploymentMap.containsKey(path)) {
                    throw subDeploymentNotFound(path, subDeploymentMap.keySet());
                }
                final ResourceRoot subDeployment = subDeploymentMap.get(path);
                subDeployment.putAttachment(SUB_DEPLOYMENT_STRUCTURE, spec);

                // cascade the exclusions if configured
                if(moduleSpec.isExclusionsCascadedToSubDeployments() && rootDeploymentSpecification != null) {
                    for(String exclusion : rootDeploymentSpecification.getExclusionsList()) {
                        spec.addExclusion(exclusion);
                    }
                }
            }


        } catch (IOException e) {
            throw new DeploymentUnitProcessingException(e);
        }
    }

    private void handleDeployment(final DeploymentPhaseContext phaseContext, final DeploymentUnit deploymentUnit,
                                  final ModuleSpecification moduleSpec, final ModuleStructureSpec rootDeploymentSpecification,
                                  final Map<String, AdditionalModuleSpecification> additionalModules)
            throws DeploymentUnitProcessingException {
        final Map<VirtualFile, ResourceRoot> resourceRoots = resourceRoots(deploymentUnit);
        // handle unmodifiable module dependencies with alias
        List<ModuleDependency> moduleDependencies = rootDeploymentSpecification.getModuleDependencies();
        List<ModuleDependency> aliasDependencies = new ArrayList<>();

        // Pre-index additionalmodules
        Map<String, AdditionalModuleSpecification> index = new HashMap<>();

        // Only iterate additionalModules once, instead of once per dependency
        for (AdditionalModuleSpecification module : additionalModules.values()) {
            for (String alias : module.getModuleAliases()) {
                index.put(alias, module);
            }
        }
        // No more nested loop
        ModuleDependency moduleDependency;
        for (ModuleDependency dependency : moduleDependencies) {
            String identifier = dependency.getDependencyModule();
            if (index.containsKey(identifier)) {
                moduleDependency = ModuleDependency.Builder.of(dependency.getModuleLoader(), index.get(identifier).getModuleName())
                        .setOptional(dependency.isOptional()).setExport(dependency.isExport())
                        .setImportServices(dependency.isImportServices()).setUserSpecified(dependency.isUserSpecified())
                        .build();
                aliasDependencies.add(moduleDependency);
            }
        }

        ModuleAliasChecker.checkModuleAliasesForDependencies(moduleDependencies, MessageContext.JBOSS_DEPLOYMENT_STRUCTURE_CONTEXT, deploymentUnit.getName());
        moduleSpec.addUserDependencies(moduleDependencies);
        List<String> exclusionsList = rootDeploymentSpecification.getExclusionsList();
        for(String exclusion : exclusionsList) {
            ModuleAliasChecker.checkModuleAliasesForExclusion(exclusion, MessageContext.JBOSS_DEPLOYMENT_STRUCTURE_CONTEXT, deploymentUnit.getName());
            moduleSpec.addModuleExclusion(exclusion);
        }
        for (String alias : rootDeploymentSpecification.getAliasesList()) {
            moduleSpec.addModuleAlias(alias);
        }
        moduleSpec.addModuleSystemDependencies(rootDeploymentSpecification.getSystemDependencies());
        for (final ResourceRoot additionalResourceRoot : rootDeploymentSpecification.getResourceRoots()) {

            final ResourceRoot existingRoot = resourceRoots.get(additionalResourceRoot.getRoot());
            if (existingRoot != null) {
                //we already have to the resource root
                //so now we want to merge it
                existingRoot.merge(additionalResourceRoot);
            } else if (!additionalResourceRoot.getRoot().exists()) {
                ServerLogger.DEPLOYMENT_LOGGER.additionalResourceRootDoesNotExist(additionalResourceRoot.getRoot().getPathName());
            } else {
                deploymentUnit.addToAttachmentList(Attachments.RESOURCE_ROOTS, additionalResourceRoot);
                //compute the annotation index for the root
                ResourceRootIndexer.indexResourceRoot(additionalResourceRoot);
                ModuleRootMarker.mark(additionalResourceRoot);
            }
        }
        for (final String classTransformer : rootDeploymentSpecification.getClassTransformers()) {
            moduleSpec.addClassTransformer(classTransformer);
        }
        // handle annotations
        for (final String dependency : rootDeploymentSpecification.getAnnotationModules()) {
            String identifier = dependency;
            for (AdditionalModuleSpecification module : additionalModules.values()) {
                if (module.getModuleAliases().contains(identifier)) {
                    identifier = module.getModuleName();
                    break;
                }
            }
            deploymentUnit.addToAttachmentList(Attachments.ADDITIONAL_INDEX_MODULES, identifier);
            // additional modules will not be created till much later, a dep on them would fail
            if (identifier.startsWith(ServiceModuleLoader.MODULE_PREFIX) &&
                !(additionalModules.containsKey(identifier) || isSubdeployment(identifier, deploymentUnit))) {
                phaseContext.addToAttachmentList(Attachments.NEXT_PHASE_DEPS, ServiceModuleLoader.moduleServiceName(identifier));
            }
        }
        moduleSpec.setLocalLast(rootDeploymentSpecification.isLocalLast());

        if(rootDeploymentSpecification.getExcludedSubsystems() != null) {
            deploymentUnit.putAttachment(Attachments.EXCLUDED_SUBSYSTEMS, rootDeploymentSpecification.getExcludedSubsystems());
        }
    }

    private boolean isSubdeployment(String dependency, DeploymentUnit deploymentUnit) {
        DeploymentUnit top = deploymentUnit.getParent()==null?deploymentUnit:deploymentUnit.getParent();
        return dependency.startsWith(ServiceModuleLoader.MODULE_PREFIX.concat(top.getName()));
    }

    private Map<VirtualFile, ResourceRoot> resourceRoots(final DeploymentUnit deploymentUnit) {
        final Map<VirtualFile, ResourceRoot> resourceRoots = new HashMap<>();
        for (final ResourceRoot root : DeploymentUtils.allResourceRoots(deploymentUnit)) {
            resourceRoots.put(root.getRoot(), root);
        }
        return resourceRoots;
    }

    private DeploymentUnitProcessingException subDeploymentNotFound(final String path, final Collection<String> subDeployments) {
        final StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (final String dep : subDeployments) {
            if (!first) {
                builder.append(", ");
            } else {
                first = false;
            }
            builder.append(dep);
        }
        return ServerLogger.ROOT_LOGGER.subdeploymentNotFound(path, builder);
    }

    @Override
    public void undeploy(final DeploymentUnit context) {
        //any processors from these subsystems that run before this DUP
        //will have run, so we need to clear this to make sure their undeploy
        //will be called
        context.removeAttachment(Attachments.EXCLUDED_SUBSYSTEMS);
    }

    private ParseResult parse(final File file, final DeploymentUnit deploymentUnit, final ModuleLoader moduleLoader) throws DeploymentUnitProcessingException {
        final FileInputStream fis;
        try {
            fis = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            throw ServerLogger.ROOT_LOGGER.deploymentStructureFileNotFound(file);
        }
        try {
            return parse(fis, file, deploymentUnit, moduleLoader);
        } finally {
            safeClose(fis);
        }
    }

    private void setIfSupported(final XMLInputFactory inputFactory, final String property, final Object value) {
        if (inputFactory.isPropertySupported(property)) {
            inputFactory.setProperty(property, value);
        }
    }

    private ParseResult parse(final InputStream source, final File file, final DeploymentUnit deploymentUnit, final ModuleLoader moduleLoader)
            throws DeploymentUnitProcessingException {
        try {

            final XMLInputFactory inputFactory = INPUT_FACTORY;
            setIfSupported(inputFactory, XMLInputFactory.IS_VALIDATING, Boolean.FALSE);
            setIfSupported(inputFactory, XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            final XMLStreamReader streamReader = inputFactory.createXMLStreamReader(source);
            try {
                final ParseResult result = new ParseResult(moduleLoader, deploymentUnit);
                mapper.parseDocument(result, streamReader);
                return result;
            } finally {
                safeClose(streamReader);
            }
        } catch (XMLStreamException e) {
            throw ServerLogger.ROOT_LOGGER.errorLoadingDeploymentStructureFile(file.getPath(), e);
        }
    }

    private static void safeClose(final Closeable closeable) {
        if (closeable != null)
            try {
                closeable.close();
            } catch (IOException e) {
                // ignore
            }
    }

    private static void safeClose(final XMLStreamReader streamReader) {
        if (streamReader != null)
            try {
                streamReader.close();
            } catch (XMLStreamException e) {
                // ignore
            }
    }


}

package org.wildfly.core.instmgr;

import org.jboss.as.controller.AbstractControllerService;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelControllerServiceInitialization;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.PlaceholderResource;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.installationmanager.InstallationManagerFinder;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

import java.util.Optional;
import java.util.function.Supplier;

import static org.jboss.as.controller.AbstractControllerService.PATH_MANAGER_CAPABILITY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;

public final class InstMgrInitialization implements ModelControllerServiceInitialization {

    @Override
    public void initializeStandalone(ServiceTarget target, ManagementModel managementModel, AbstractControllerService controllerService) {
        Optional<InstallationManagerFactory> im = InstallationManagerFinder.find();
        if (im.isPresent()) {

            ServiceBuilder<?> serviceBuilder = target.addService(InstMgrResourceDefinition.INSTALLATION_MANAGER_CAPABILITY.getCapabilityServiceName());
            Supplier<PathManager> pathManagerSupplier = serviceBuilder.requires(PATH_MANAGER_CAPABILITY.getCapabilityServiceName());
            InstMgrService imService = new InstMgrService(pathManagerSupplier);
            serviceBuilder.setInstance(imService).setInitialMode(ServiceController.Mode.PASSIVE)
                    .install();

            managementModel.getRootResource().registerChild(InstMgrResourceDefinition.getPath(InstMgrConstants.TOOL_NAME), PlaceholderResource.INSTANCE);
            managementModel.getRootResourceRegistration().registerSubModel(new InstMgrResourceDefinition(im.get(), controllerService, imService));
        }
    }

    @Override
    public void initializeDomain(ServiceTarget target, ManagementModel managementModel) {
        // Not available
    }

    @Override
    public void initializeHost(ServiceTarget target, ManagementModel managementModel, String hostName, AbstractControllerService controllerService) {
        Optional<InstallationManagerFactory> im = InstallationManagerFinder.find();
        if (im.isPresent()) {
            final PathElement host = PathElement.pathElement(HOST, hostName);
            final ManagementResourceRegistration hostRegistration = managementModel.getRootResourceRegistration().getSubModel(PathAddress.EMPTY_ADDRESS.append(host));
            final Resource hostResource = managementModel.getRootResource().getChild(host);
            if (hostResource == null) {
                // this is generally only the case when an embedded HC has been started with an empty config, but /host=foo:add() has not yet been invoked, so we have no
                // real hostname yet.
                return;
            }

            ServiceBuilder<?> serviceBuilder = target.addService(InstMgrResourceDefinition.INSTALLATION_MANAGER_CAPABILITY.getCapabilityServiceName());
            Supplier<PathManager> pathManagerSupplier = serviceBuilder.requires(PATH_MANAGER_CAPABILITY.getCapabilityServiceName());
            InstMgrService imService = new InstMgrService(pathManagerSupplier);
            serviceBuilder.setInstance(imService).setInitialMode(ServiceController.Mode.PASSIVE)
                    .install();

            hostResource.registerChild(InstMgrResourceDefinition.getPath(InstMgrConstants.TOOL_NAME), PlaceholderResource.INSTANCE);
            hostRegistration.registerSubModel(new InstMgrResourceDefinition(im.get(), controllerService, imService));
        }
    }
}

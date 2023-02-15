package org.wildfly.core.instmgr;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.dmr.ModelType;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

abstract class AbstractInstMgrChannelsHandler extends InstMgrOperationStepHandler {


    AbstractInstMgrChannelsHandler(InstMgrService imService, InstallationManagerFactory imf) {
        super(imService, imf);
    }
}

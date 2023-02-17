package org.wildfly.core.instmgr;

import org.wildfly.installationmanager.spi.InstallationManagerFactory;

abstract class AbstractInstMgrChannelsHandler extends InstMgrOperationStepHandler {


    AbstractInstMgrChannelsHandler(InstMgrService imService, InstallationManagerFactory imf) {
        super(imService, imf);
    }
}

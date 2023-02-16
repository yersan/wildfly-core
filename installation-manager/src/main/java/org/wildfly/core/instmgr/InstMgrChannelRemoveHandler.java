package org.wildfly.core.instmgr;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

class InstMgrChannelRemoveHandler extends InstMgrOperationStepHandler {

    InstMgrChannelRemoveHandler(InstMgrService imService, InstallationManagerFactory imf) {
        super(imService, imf);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

    }
}

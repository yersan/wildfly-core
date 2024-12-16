/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.jboss.as.model.test.FailedOperationTransformationConfig.REJECTED_RESOURCE;
import static org.junit.Assert.assertTrue;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.TRUST_MANAGER;

import java.util.EnumSet;
import java.util.List;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.model.test.FailedOperationTransformationConfig;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests of transformation of the elytron subsystem to previous API versions.
 *
 * @author Brian Stansberry
 * @author Tomaz Cerar
 */
@RunWith(Parameterized.class)
public class SubsystemTransformerTestCase extends AbstractSubsystemTest {

    @Parameters
    public static Iterable<ModelTestControllerVersion> parameters() {
        return EnumSet.of(ModelTestControllerVersion.EAP_7_4_0, ModelTestControllerVersion.EAP_8_0_0, ModelTestControllerVersion.WILDFLY_31_0_0);
    }

    private static final PathAddress SUBSYSTEM_ADDRESS = PathAddress.pathAddress(ModelDescriptionConstants.SUBSYSTEM, ElytronExtension.SUBSYSTEM_NAME);

    private final ModelTestControllerVersion controllerVersion;
    private final org.jboss.as.subsystem.test.AdditionalInitialization additionalInitialization;
    private final ModelVersion version;

    public SubsystemTransformerTestCase(ModelTestControllerVersion controller) {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension(), controller.getStability());
        this.controllerVersion = controller;
        this.version = this.getModelVersion();
        this.additionalInitialization = AdditionalInitialization.fromModelTestControllerVersion(controller);

    }

    private ModelVersion getModelVersion() {
        switch (this.controllerVersion) {
            case EAP_7_4_0:
                return ElytronExtension.ELYTRON_13_0_0;
            case EAP_8_0_0:
            case WILDFLY_31_0_0:
                return ElytronExtension.ELYTRON_18_0_0;
            default:
                throw new IllegalArgumentException();
        }
    }

    /**
     * Test case testing resources and attributes are appropriately rejected when transforming to EAP 7.4.
     */
    @Test
    public void testRejectingTransformers() throws Exception {
        String subsystemXmlFile = String.format((this.controllerVersion.getStability() == Stability.DEFAULT) ? "elytron-transformers-%d.%d-reject.xml" : "elytron-transformers-%d.%d-%s-reject.xml", this.version.getMajor(), this.version.getMinor(), this.controllerVersion.getStability());
        FailedOperationTransformationConfig config = new FailedOperationTransformationConfig();
        if (this.version.compareTo(ElytronExtension.ELYTRON_18_0_0) >= 0) {
            // TODO add missing rejection validation for EAP 8.0
            if (this.controllerVersion.getStability().enables(Stability.COMMUNITY)) {
                config.addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.DYNAMIC_CLIENT_SSL_CONTEXT, "dynamicClientSSLContext")), REJECTED_RESOURCE);
            }
        }
        if (this.version.compareTo(ElytronExtension.ELYTRON_13_0_0) >= 0) {
            config.addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.SERVER_SSL_SNI_CONTEXT, "SNIwithCaret")),
                        new FailedOperationTransformationConfig.NewAttributesConfig(ElytronDescriptionConstants.HOST_CONTEXT_MAP)
                )
            .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.PROPERTIES_REALM, "PropertiesRealmEncodingCharset")),
                    FailedOperationTransformationConfig.REJECTED_RESOURCE)
            .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.FILESYSTEM_REALM, "FilesystemRealmEncodingCharset")),
                    FailedOperationTransformationConfig.REJECTED_RESOURCE)
            .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.FILESYSTEM_REALM, "FilesystemRealmEncrypted")),
                    FailedOperationTransformationConfig.REJECTED_RESOURCE)
            .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.FILESYSTEM_REALM, "FilesystemRealmIntegrity")),
                    FailedOperationTransformationConfig.REJECTED_RESOURCE)
            .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.JDBC_REALM, "JDBCRealmCharset")),
                    FailedOperationTransformationConfig.REJECTED_RESOURCE)
            .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.LDAP_REALM, "LDAPRealmEncodingCharset")),
                    FailedOperationTransformationConfig.REJECTED_RESOURCE)
            .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.DISTRIBUTED_REALM, "DistributedRealmFirstUnavailableIgnoredEventEmitted")),
                    FailedOperationTransformationConfig.REJECTED_RESOURCE)
            .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(TRUST_MANAGER, "TrustManagerCrls")), REJECTED_RESOURCE)
            .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.SERVER_SSL_CONTEXT, "ctxSSLv2Hello")),
                    REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.CLIENT_SSL_CONTEXT, "ClientContextSSLv2Hello")),
                        REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.JAAS_REALM, "myJaasRealm")), REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.VIRTUAL_SECURITY_DOMAIN, "myVirtualDomain")), REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.SECURITY_DOMAIN, "myDomain")), REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.FILE_AUDIT_LOG)),
                    new FailedOperationTransformationConfig.NewAttributesConfig(AuditResourceDefinitions.ENCODING))
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.PERIODIC_ROTATING_FILE_AUDIT_LOG)),
                    new FailedOperationTransformationConfig.NewAttributesConfig(AuditResourceDefinitions.ENCODING))
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.SIZE_ROTATING_FILE_AUDIT_LOG)),
                    new FailedOperationTransformationConfig.NewAttributesConfig(AuditResourceDefinitions.ENCODING));
        }
        testRejectingTransformers(subsystemXmlFile, config);
    }

    /**
     * Test case testing resources and attributes are appropriately transformed when transforming to EAP 7.4.
     */
    @Test
    public void testTransformer() throws Exception {
        final ModelVersion version = this.controllerVersion.getSubsystemModelVersion(getMainSubsystemName());
        String subsystemXml = this.readResource(String.format((this.controllerVersion.getStability() == Stability.DEFAULT) ? "elytron-transformers-%d.%d.xml" : "elytron-transformers-%d.%d-%s.xml", this.version.getMajor(), this.version.getMinor(), this.controllerVersion.getStability()));

        KernelServices services = this.buildKernelServices(subsystemXml, version,
                this.controllerVersion.getCoreMavenGroupId() + ":wildfly-elytron-integration:" + this.controllerVersion.getCoreVersion());

        // check that both versions of the legacy model are the same and valid
        checkSubsystemModelTransformation(services, version, null, false);

        ModelNode transformed = services.readTransformedModel(version);
        Assert.assertTrue(transformed.isDefined());
    }

    private KernelServices buildKernelServices(String xml, ModelVersion version, String... mavenResourceURLs) throws Exception {
        KernelServicesBuilder builder = this.createKernelServicesBuilder(this.additionalInitialization).setSubsystemXml(xml);

        builder.createLegacyKernelServicesBuilder(this.additionalInitialization, this.controllerVersion, version)
                .addMavenResourceURL(mavenResourceURLs)
                .skipReverseControllerCheck()
                .addParentFirstClassPattern("org.jboss.as.controller.logging.ControllerLogger*")
                .addParentFirstClassPattern("org.jboss.as.controller.PathAddress")
                .addParentFirstClassPattern("org.jboss.as.controller.PathElement")
                .addParentFirstClassPattern("org.jboss.as.server.logging.*")
                .addParentFirstClassPattern("org.jboss.logging.*")
                .addParentFirstClassPattern("org.jboss.dmr.*")
                .dontPersistXml();

        KernelServices services = builder.build();
        Assert.assertTrue(ModelTestControllerVersion.MASTER + " boot failed", services.isSuccessfulBoot());
        Assert.assertTrue(this.controllerVersion.getMavenGavVersion() + " boot failed", services.getLegacyServices(version).isSuccessfulBoot());
        return services;
    }

    private void testRejectingTransformers(final String subsystemXmlFile, final FailedOperationTransformationConfig config) throws Exception {
        ModelVersion elytronVersion = this.controllerVersion.getSubsystemModelVersion(getMainSubsystemName());

        //Boot up empty controllers with the resources needed for the ops coming from the xml to work
        KernelServicesBuilder builder = createKernelServicesBuilder(AdditionalInitialization.withCapabilities(this.controllerVersion.getStability(),
                        RuntimeCapability.buildDynamicCapabilityName(Capabilities.DATA_SOURCE_CAPABILITY_NAME, "ExampleDS")
        ));
        builder.createLegacyKernelServicesBuilder(AdditionalInitialization.MANAGEMENT, this.controllerVersion, elytronVersion)
                .addMavenResourceURL(this.controllerVersion.getCoreMavenGroupId() + ":wildfly-elytron-integration:" + this.controllerVersion.getCoreVersion())
                .addParentFirstClassPattern("org.jboss.as.controller.logging.ControllerLogger*")
                .addParentFirstClassPattern("org.jboss.as.controller.PathAddress")
                .addParentFirstClassPattern("org.jboss.as.controller.PathElement")
                .addParentFirstClassPattern("org.jboss.as.server.logging.*")
                .addParentFirstClassPattern("org.jboss.logging.*")
                .addParentFirstClassPattern("org.jboss.dmr.*")
                .dontPersistXml();

        KernelServices mainServices = builder.build();
        assertTrue(mainServices.isSuccessfulBoot());
        assertTrue(mainServices.getLegacyServices(elytronVersion).isSuccessfulBoot());

        List<ModelNode> ops = builder.parseXmlResource(subsystemXmlFile);
        ModelTestUtils.checkFailedTransformedBootOperations(mainServices, elytronVersion, ops, config);
    }

}

package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for GoogleAppsConnector schema validation and configuration
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsSchemaTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test schema retrieval and structure")
    public void testSchema() {
        // Get schema
        Schema schema = connectorFacade.schema();

        // Verify schema can be retrieved correctly
        assertThat(schema).isNotNull();

        // Verify supported ObjectClasses
        Set<ObjectClassInfo> objectClasses = schema.getObjectClassInfo();
        assertThat(objectClasses).isNotEmpty();
        assertThat(objectClasses).hasSize(5); // ACCOUNT, GROUP, OrgUnit, Member, LicenseAssignment

        // Verify ACCOUNT class
        ObjectClassInfo accountClass = schema.findObjectClassInfo(ObjectClass.ACCOUNT_NAME);
        assertThat(accountClass).isNotNull();
        assertThat(accountClass.getAttributeInfo()).isNotEmpty();

        // Verify GROUP class
        ObjectClassInfo groupClass = schema.findObjectClassInfo(ObjectClass.GROUP_NAME);
        assertThat(groupClass).isNotNull();
        assertThat(groupClass.getAttributeInfo()).isNotEmpty();

        // Verify OrgUnit class
        ObjectClassInfo orgUnitClass = schema.findObjectClassInfo("OrgUnit");
        assertThat(orgUnitClass).isNotNull();
        assertThat(orgUnitClass.getAttributeInfo()).isNotEmpty();

        // Verify Member class
        ObjectClassInfo memberClass = schema.findObjectClassInfo("Member");
        assertThat(memberClass).isNotNull();
        assertThat(memberClass.getAttributeInfo()).isNotEmpty();

        // Verify LicenseAssignment class
        ObjectClassInfo licenseClass = schema.findObjectClassInfo("LicenseAssignment");
        assertThat(licenseClass).isNotNull();
        assertThat(licenseClass.getAttributeInfo()).isNotEmpty();
    }

    @Test
    @DisplayName("Test configuration validation")
    public void testConfigurationValidation() {
        // Verify configuration is validated correctly
        GoogleAppsConfiguration config = createTestConfiguration();

        // Valid configuration does not throw exception
        assertThatCode(() -> config.validate()).doesNotThrowAnyException();

        // Invalid configuration (domain is null) throws exception
        GoogleAppsConfiguration invalidConfig = createTestConfiguration();
        invalidConfig.setDomain(null);
        assertThatThrownBy(() -> invalidConfig.validate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Domain cannot be null");
    }

    @Test
    @DisplayName("Test ACCOUNT class attributes")
    public void testAccountClassAttributes() {
        Schema schema = connectorFacade.schema();
        ObjectClassInfo accountClass = schema.findObjectClassInfo(ObjectClass.ACCOUNT_NAME);

        // Verify key attributes exist
        assertThat(accountClass.getAttributeInfo().stream()
                .anyMatch(attr -> attr.getName().equals("givenName"))).isTrue();
        assertThat(accountClass.getAttributeInfo().stream()
                .anyMatch(attr -> attr.getName().equals("familyName"))).isTrue();
        assertThat(accountClass.getAttributeInfo().stream()
                .anyMatch(attr -> attr.getName().equals("__ENABLE__"))).isTrue();
        assertThat(accountClass.getAttributeInfo().stream()
                .anyMatch(attr -> attr.getName().equals("__PASSWORD__"))).isTrue();
        assertThat(accountClass.getAttributeInfo().stream()
                .anyMatch(attr -> attr.getName().equals("__GROUPS__"))).isTrue();
        assertThat(accountClass.getAttributeInfo().stream()
                .anyMatch(attr -> attr.getName().equals("__PHOTO__"))).isTrue();
    }

    @Test
    @DisplayName("Test GROUP class attributes")
    public void testGroupClassAttributes() {
        Schema schema = connectorFacade.schema();
        ObjectClassInfo groupClass = schema.findObjectClassInfo(ObjectClass.GROUP_NAME);

        // Verify key attributes exist
        assertThat(groupClass.getAttributeInfo().stream()
                .anyMatch(attr -> attr.getName().equals("name"))).isTrue();
        assertThat(groupClass.getAttributeInfo().stream()
                .anyMatch(attr -> attr.getName().equals("__NAME__"))).isTrue();
        assertThat(groupClass.getAttributeInfo().stream()
                .anyMatch(attr -> attr.getName().equals("__DESCRIPTION__"))).isTrue();
    }
}
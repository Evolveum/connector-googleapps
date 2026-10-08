package com.evolveum.polygon.connector.googleapps.integration;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.Reporter;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.evolveum.polygon.connector.googleapps.GoogleAppsConnector.FAMILY_NAME_ATTR;
import static com.evolveum.polygon.connector.googleapps.GoogleAppsConnector.GIVEN_NAME_ATTR;
import static com.evolveum.polygon.connector.googleapps.GoogleAppsConnector.ORG_UNIT_PATH_ATTR;
import static org.testng.Assert.*;

/** Opt-in live POC. This class deliberately does not match Surefire's ordinary *Test patterns. */
public class UserCrudIT extends GoogleAppsIntegrationSupport {
    @Test(groups = "integration")
    public void createsReadsUpdatesAndDeletesUser() throws Exception {
        String runId = UUID.randomUUID().toString().replace("-", "");
        testEmail = "gws-it-" + runId + "@" + settings.connectorConfiguration().getDomain();
        familyMarker = "Poc-" + runId;
        Reporter.log("Temporary POC user (also the manual cleanup key): " + testEmail, true);

        // Unlike connector.test(), this executes a real authenticated Directory request.
        assertNull(readUser(new Uid(testEmail)), "Generated test email must not already exist");

        GuardedString password = new GuardedString(("Gws!9aA-" + UUID.randomUUID()).toCharArray());
        try {
            Set<Attribute> attributes = new HashSet<>();
            attributes.add(new Name(testEmail));
            attributes.add(AttributeBuilder.build(GIVEN_NAME_ATTR, "Integration"));
            attributes.add(AttributeBuilder.build(FAMILY_NAME_ATTR, familyMarker));
            attributes.add(AttributeBuilder.build(OperationalAttributes.PASSWORD_NAME, password));
            if (!settings.orgUnitPath().isEmpty()) {
                attributes.add(AttributeBuilder.build(ORG_UNIT_PATH_ATTR, settings.orgUnitPath()));
            }
            Reporter.log("CREATE user", true);
            createAttempted = true;
            createdUid = connector.create(ObjectClass.ACCOUNT, attributes, options);
        } finally {
            password.dispose();
        }
        assertNotNull(createdUid, "Create must return a UID");
        assertFalse(createdUid.getUidValue().isEmpty(), "Create must return a nonempty UID");

        Reporter.log("READ user and verify initial values", true);
        assertUserState("Integration");

        Reporter.log("UPDATE givenName", true);
        String originalId = createdUid.getUidValue();
        createdUid = connector.update(ObjectClass.ACCOUNT, createdUid,
                Set.of(AttributeBuilder.build(GIVEN_NAME_ATTR, "IntegrationUpdated")), options);
        assertNotNull(createdUid, "Update must return a UID");
        assertEquals(createdUid.getUidValue(), originalId, "Updating givenName must preserve identity");
        assertUserState("IntegrationUpdated");

        Reporter.log("DELETE user and verify absence", true);
        connector.delete(ObjectClass.ACCOUNT, createdUid, options);
        awaitUser(createdUid, "deletion", actual -> assertNull(actual, "Deleted user must be absent"));
        deletionConfirmed = true;
    }

    private void assertUserState(String givenName) throws InterruptedException {
        awaitUser(createdUid, "givenName=" + givenName, actual -> {
            assertNotNull(actual, "Created user must be readable");
            assertEquals(actual.getUid().getUidValue(), createdUid.getUidValue());
            assertEquals(actual.getName().getNameValue(), testEmail);
            assertEquals(AttributeUtil.getStringValue(actual.getAttributeByName(GIVEN_NAME_ATTR)), givenName);
            assertEquals(AttributeUtil.getStringValue(actual.getAttributeByName(FAMILY_NAME_ATTR)), familyMarker,
                    "Updating givenName must preserve familyName");
        });
    }
}

package com.evolveum.polygon.connector.googleapps.integration;

import com.evolveum.polygon.connector.googleapps.GoogleAppsConnector;
import com.evolveum.polygon.connector.googleapps.GoogleAppsConfiguration;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.testng.Reporter;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.evolveum.polygon.connector.googleapps.GoogleAppsConnector.FAMILY_NAME_ATTR;
import static com.evolveum.polygon.connector.googleapps.GoogleAppsConnector.GIVEN_NAME_ATTR;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** Minimal support for one owned user fixture; integration runs must remain sequential. */
public abstract class GoogleAppsIntegrationSupport {
    protected GoogleAppsConnector connector;
    protected IntegrationConfiguration settings;
    protected GoogleAppsConfiguration configuration;
    protected String orgUnitPath;
    protected GoogleDirectoryMockServer mockServer;
    protected Uid createdUid;
    protected String testEmail;
    protected String familyMarker;
    protected boolean createAttempted;
    protected boolean deletionConfirmed;

    protected final OperationOptions options = new OperationOptionsBuilder()
            .setAttributesToGet(GIVEN_NAME_ATTR, FAMILY_NAME_ATTR).build();

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        String target = System.getProperty("gws.it.target", "live");
        if (!"live".equals(target) && !"mock".equals(target)) {
            throw new IllegalArgumentException("gws.it.target must be live or mock");
        }
        if ("live".equals(target) && !"true".equalsIgnoreCase(System.getProperty("gws.it.enabled", "false"))) {
            throw new SkipException("Live Google CRUD test is disabled; use -Dgws.it.enabled=true explicitly");
        }
        createdUid = null;
        testEmail = null;
        familyMarker = null;
        createAttempted = false;
        deletionConfirmed = false;
        if ("mock".equals(target)) {
            mockServer = new GoogleDirectoryMockServer();
            configuration = new MockGoogleAppsConfiguration(mockServer);
            orgUnitPath = "";
            Reporter.log("CRUD target: local mock server " + mockServer.rootUrl(), true);
        } else {
            settings = IntegrationConfiguration.load();
            configuration = settings.connectorConfiguration();
            orgUnitPath = settings.orgUnitPath();
            Reporter.log("CRUD target: live Google Workspace", true);
        }
        connector = new GoogleAppsConnector();
        connector.init(configuration);
    }

    protected ConnectorObject readUser(Uid uid) {
        List<ConnectorObject> results = new ArrayList<>();
        connector.executeQuery(ObjectClass.ACCOUNT, new EqualsFilter(uid), object -> {
            results.add(object);
            return true;
        }, options);
        assertTrue(results.size() <= 1, "A UID read must return at most one user");
        return results.isEmpty() ? null : results.get(0);
    }

    /** Retry state assertions, not arbitrary authentication, permission or API errors. */
    protected void awaitUser(Uid uid, String state, Consumer<ConnectorObject> assertion) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        while (true) {
            ConnectorObject actual = readUser(uid);
            try {
                assertion.accept(actual);
                return;
            } catch (AssertionError failure) {
                if (System.nanoTime() >= deadline) {
                    throw new AssertionError("Timed out waiting for " + state + " for " + testEmail, failure);
                }
            }
            Thread.sleep(2000);
        }
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        try {
            if (connector != null && createAttempted && !deletionConfirmed) {
                // A create may have succeeded remotely before a later connector step failed.
                // Use the exact generated email, never a broad search or prefix-based deletion.
                Uid key = createdUid != null ? createdUid : new Uid(testEmail);
                ConnectorObject remaining = readUser(key);
                if (remaining != null) {
                    assertEquals(remaining.getName().getNameValue(), testEmail, "Cleanup ownership: email");
                    assertEquals(remaining.getAttributeByName(FAMILY_NAME_ATTR).getValue().get(0),
                            familyMarker, "Cleanup ownership: run marker");
                    Reporter.log("Cleaning up temporary user: " + testEmail, true);
                    try {
                        connector.delete(ObjectClass.ACCOUNT, remaining.getUid(), options);
                    } catch (UnknownUidException alreadyRemoved) {
                        // Cleanup is idempotent; the lifecycle test itself does not swallow this error.
                    }
                    awaitUser(key, "cleanup deletion", org.testng.Assert::assertNull);
                }
            }
        } finally {
            try {
                if (connector != null) {
                    connector.dispose();
                    connector = null;
                }
                if (settings != null) {
                    settings.close();
                    settings = null;
                }
            } finally {
                if (mockServer != null) {
                    mockServer.close();
                    mockServer = null;
                }
            }
        }
    }
}

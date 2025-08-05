package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for GoogleAppsConnector User search filter translation to Google API query parameters.
 * <p>
 * This test class focuses on verifying that ConnID filters are correctly translated
 * to Google API query parameters and request parameters. ConnectorObject mapping
 * is tested in other test classes.
 *
 * @author Hiroyuki Wada
 */
@DisplayName("GoogleApps User Search - Filter Translation Tests")
public class GoogleAppsUserSearchFilterTest extends GoogleAppsConnectorTestBase {

    // ========== EQUALS FILTER TESTS ==========

    @Test
    @DisplayName("Test EqualsFilter with givenName")
    public void testEqualsFilterGivenName() {
        // Setup minimal mock response
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("givenName=John"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"users\": []}")));

        // Create EqualsFilter for givenName
        Filter filter = FilterBuilder.equalTo(AttributeBuilder.build("givenName", "John"));

        // Execute search with filter
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Verify request was sent with correct query parameter
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("givenName=John")));
    }

    @Test
    @DisplayName("Test EqualsFilter with familyName")
    public void testEqualsFilterFamilyName() {
        // Setup minimal mock response
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("familyName=Smith"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"users\": []}")));

        // Create EqualsFilter for familyName
        Filter filter = FilterBuilder.equalTo(AttributeBuilder.build("familyName", "Smith"));

        // Execute search with filter
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Verify request was sent with correct query parameter
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("familyName=Smith")));
    }

    @Test
    @DisplayName("Test EqualsFilter with email")
    public void testEqualsFilterEmail() {
        // Setup minimal mock response
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("email=test@example.com"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"users\": []}")));

        // Create EqualsFilter for email
        Filter filter = FilterBuilder.equalTo(AttributeBuilder.build("email", "test@example.com"));

        // Execute search with filter
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Verify request was sent with correct query parameter
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("email=test@example.com")));
    }

    @Test
    @DisplayName("Test EqualsFilter with isAdmin")
    public void testEqualsFilterIsAdmin() {
        // Setup minimal mock response
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("isAdmin=true"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"users\": []}")));

        // Create EqualsFilter for isAdmin
        Filter filter = FilterBuilder.equalTo(AttributeBuilder.build("isAdmin", "true"));

        // Execute search with filter
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Verify request was sent with correct query parameter
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("isAdmin=true")));
    }

    // ========== CONTAINS FILTER TESTS ==========

    @Test
    @DisplayName("Test ContainsFilter with givenName")
    public void testContainsFilterGivenName() {
        // Setup minimal mock response
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("givenName:John"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"users\": []}")));

        // Create ContainsFilter for givenName
        Filter filter = FilterBuilder.contains(AttributeBuilder.build("givenName", "John"));

        // Execute search with filter
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Verify request was sent with correct query parameter
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("givenName:John")));
    }

    @Test
    @DisplayName("Test ContainsFilter with familyName")
    public void testContainsFilterFamilyName() {
        // Setup minimal mock response
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("familyName:Smith"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"users\": []}")));

        // Create ContainsFilter for familyName
        Filter filter = FilterBuilder.contains(AttributeBuilder.build("familyName", "Smith"));

        // Execute search with filter
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Verify request was sent with correct query parameter
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("familyName:Smith")));
    }

    @Test
    @DisplayName("Test ContainsFilter with email")
    public void testContainsFilterEmail() {
        // Setup minimal mock response
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("email:example.com"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"users\": []}")));

        // Create ContainsFilter for email
        Filter filter = FilterBuilder.contains(AttributeBuilder.build("email", "example.com"));

        // Execute search with filter
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Verify request was sent with correct query parameter
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("email:example.com")));
    }

    @Test
    @DisplayName("Test ContainsFilter with __NAME__ (should map to email search)")
    public void testContainsFilterName() {
        // Setup minimal mock response
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("email:admin"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"users\": []}")));

        // Create ContainsFilter for __NAME__
        Filter filter = FilterBuilder.contains(AttributeBuilder.build(Name.NAME, "admin"));

        // Execute search with filter
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Verify request was sent with correct query parameter (maps to email search)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("email:admin")));
    }

    @Test
    @DisplayName("Test ContainsFilter with emails (should map to email search)")
    public void testContainsFilterEmails() {
        // Setup minimal mock response
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("email:company.com"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"users\": []}")));

        // Create ContainsFilter for emails
        Filter filter = FilterBuilder.contains(AttributeBuilder.build("emails", "company.com"));

        // Execute search with filter
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Verify request was sent with correct query parameter (maps to email search)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("email:company.com")));
    }


    // ========== STARTSWITH FILTER TESTS ==========

    @Test
    @DisplayName("Test StartsWithFilter with givenName")
    public void testStartsWithFilterGivenName() {
        // Setup minimal mock response
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("givenName:Jo*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"users\": []}")));

        // Create StartsWithFilter for givenName
        Filter filter = FilterBuilder.startsWith(AttributeBuilder.build("givenName", "Jo"));

        // Execute search with filter
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Verify request was sent with correct query parameter
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("givenName:Jo*")));
    }

    @Test
    @DisplayName("Test StartsWithFilter with familyName")
    public void testStartsWithFilterFamilyName() {
        // Setup minimal mock response
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("familyName:Sm*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"users\": []}")));

        // Create StartsWithFilter for familyName
        Filter filter = FilterBuilder.startsWith(AttributeBuilder.build("familyName", "Sm"));

        // Execute search with filter
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Verify request was sent with correct query parameter
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("familyName:Sm*")));
    }

    @Test
    @DisplayName("Test StartsWithFilter with email")
    public void testStartsWithFilterEmail() {
        // Setup minimal mock response
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("email:admin*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"users\": []}")));

        // Create StartsWithFilter for email
        Filter filter = FilterBuilder.startsWith(AttributeBuilder.build("email", "admin"));

        // Execute search with filter
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Verify request was sent with correct query parameter
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("email:admin*")));
    }

    // ========== SPECIAL FILTER TESTS (Uid/Name) ==========

    @Test
    @DisplayName("Test EqualsFilter with Uid (read by ID)")
    public void testEqualsFilterWithUid() {
        // Setup minimal mock response
        stubFor(get(urlPathEqualTo("/admin/directory/v1/users/user123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": \"user123\"}")));

        // Create EqualsFilter with Uid
        Filter filter = FilterBuilder.equalTo(new Uid("user123"));

        // Execute search with filter
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Should call individual user API, not search API
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users/user123")));
    }

    @Test
    @DisplayName("Test EqualsFilter with Name (read by email)")
    public void testEqualsFilterWithName() {
        // Setup minimal mock response
        stubFor(get(urlPathEqualTo("/admin/directory/v1/users/name.user@example.com"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": \"user456\"}")));

        // Create EqualsFilter with Name
        Filter filter = FilterBuilder.equalTo(new Name("name.user@example.com"));

        // Execute search with filter
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Should call individual user API, not search API
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users/name.user@example.com")));
    }

    // ========== AND FILTER TESTS ==========

    @Test
    @DisplayName("Test AndFilter with multiple supported attributes")
    public void testAndFilter() {
        // Test that AND filters are properly translated to space-separated query clauses
        // according to Google API specification: "multiple fields in a query, add each search clause, separated by a space"

        // Setup minimal mock response expecting space-separated query
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("givenName=John familyName=Doe"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"users\": []}")));

        // Create AndFilter with multiple attributes
        Filter filter = FilterBuilder.and(
                FilterBuilder.equalTo(AttributeBuilder.build("givenName", "John")),
                FilterBuilder.equalTo(AttributeBuilder.build("familyName", "Doe"))
        );

        // Execute search with filter
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Verify request was sent with space-separated query (implicit AND)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("givenName=John familyName=Doe")));
    }

    @Test
    @DisplayName("Test AndFilter with UID priority (UID + search filter)")
    public void testAndFilterUidPriority() {
        // Test that AND filters with UID operations prioritize UID search
        // Since UID is unique, other conditions are redundant - UID search gives the intended result
        // GoogleFilterTranslator returns the UID filter, ignoring other conditions

        // Setup minimal mock response for read-by-id operation
        stubFor(get(urlPathEqualTo("/admin/directory/v1/users/user123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": \"user123\", \"primaryEmail\": \"user123@example.com\"}")));

        // Create AndFilter with Uid (read operation) + search filter
        Filter filter = FilterBuilder.and(
                FilterBuilder.equalTo(new Uid("user123")),
                FilterBuilder.equalTo(AttributeBuilder.build("givenName", "John"))
        );

        // Execute search with filter
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Should process as read-by-id since UID takes precedence (UID is unique identifier)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users/user123")));
    }

    @Test
    @DisplayName("Test AndFilter with Name priority (Name + search filter)")
    public void testAndFilterNamePriority() {
        // Test that AND filters with Name operations prioritize Name search
        // Since Name is unique, other conditions are redundant - Name search gives the intended result
        // GoogleFilterTranslator returns the Name filter, ignoring other conditions

        // Setup minimal mock response for read-by-name operation (User by primaryEmail)
        stubFor(get(urlPathEqualTo("/admin/directory/v1/users/test.user@example.com"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": \"user456\", \"primaryEmail\": \"test.user@example.com\"}")));

        // Create AndFilter with Name (read operation) + search filter
        Filter filter = FilterBuilder.and(
                FilterBuilder.equalTo(new Name("test.user@example.com")),
                FilterBuilder.equalTo(AttributeBuilder.build("givenName", "Test"))
        );

        // Execute search with ACCOUNT object class (User supports Name-based operations)
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Should process as read-by-name since Name takes precedence (Name is unique identifier)  
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users/test.user@example.com")));
    }

    @Test
    @DisplayName("Test AndFilter with UID priority over Name (UID + Name)")
    public void testAndFilterUidOverNamePriority() {
        // Test that UID takes precedence over Name when both are present in AND filter
        // This tests the priority order: UID > Name > search filters

        // Setup minimal mock response for read-by-id operation (UID priority)
        stubFor(get(urlPathEqualTo("/admin/directory/v1/users/user789"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": \"user789\", \"primaryEmail\": \"user789@example.com\"}")));

        // Create AndFilter with both UID and Name (UID should take precedence)
        Filter filter = FilterBuilder.and(
                FilterBuilder.equalTo(new Uid("user789")),
                FilterBuilder.equalTo(new Name("test.user@example.com"))
        );

        // Execute search with ACCOUNT object class
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Should process as read-by-id since UID takes precedence over Name
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users/user789")));
    }

    // ========== NEGATIVE TESTS ==========

    @Test
    @DisplayName("Test unsupported field for EqualsFilter (should throw exception)")
    public void testUnsupportedEqualsFilter() {
        Filter filter = FilterBuilder.equalTo(AttributeBuilder.build("orgUnitPath", "/TestOU"));

        assertThatThrownBy(() -> {
            connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);
        })
                .isInstanceOf(InvalidAttributeValueException.class)
                .hasMessageContaining("Field not supported for equals search: orgUnitPath");
    }

    @Test
    @DisplayName("Test unsupported field for ContainsFilter (should throw exception)")
    public void testUnsupportedContainsFilter() {
        Filter filter = FilterBuilder.contains(AttributeBuilder.build("orgUnitPath", "Test"));

        assertThatThrownBy(() -> {
            connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);
        })
                .isInstanceOf(InvalidAttributeValueException.class)
                .hasMessageContaining("Field not supported for contains search: orgUnitPath");
    }

    @Test
    @DisplayName("Test unsupported field for StartsWithFilter (should throw exception)")
    public void testUnsupportedStartsWithFilter() {
        Filter filter = FilterBuilder.startsWith(AttributeBuilder.build("orgUnitPath", "Test"));

        assertThatThrownBy(() -> {
            connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);
        })
                .isInstanceOf(InvalidAttributeValueException.class)
                .hasMessageContaining("Field not supported for starts-with search: orgUnitPath");
    }

    // ========== CUSTOMER/DOMAIN PARAMETER TESTS ==========

    @Test
    @DisplayName("Test EqualsFilter with customer parameter")
    public void testEqualsFilterCustomer() {
        // Setup minimal mock response
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("customer", equalTo("C01abc123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"users\": []}")));

        // Create EqualsFilter for customer parameter
        Filter filter = FilterBuilder.equalTo(AttributeBuilder.build("customer", "C01abc123"));

        // Execute search with filter
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Should set customer parameter, not query parameter
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("customer", equalTo("C01abc123")));
    }

    @Test
    @DisplayName("Test EqualsFilter with domain parameter")
    public void testEqualsFilterDomain() {
        // Setup minimal mock response
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("domain", equalTo("example.com"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"users\": []}")));

        // Create EqualsFilter for domain parameter
        Filter filter = FilterBuilder.equalTo(AttributeBuilder.build("domain", "example.com"));

        // Execute search with filter
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Should set domain parameter, not query parameter
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("domain", equalTo("example.com")));
    }

    @Test
    @DisplayName("Test conflicting customer and domain parameters (should throw exception)")
    public void testConflictingCustomerAndDomain() {
        // Test that customer and domain parameters cannot be used together in AND filter
        // GoogleFilter validates this conflict during query building

        Filter filter = FilterBuilder.and(
                FilterBuilder.equalTo(AttributeBuilder.build("customer", "C01abc123")),
                FilterBuilder.equalTo(AttributeBuilder.build("domain", "example.com"))
        );

        assertThatThrownBy(() -> {
            connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);
        })
                .isInstanceOf(InvalidAttributeValueException.class)
                .hasMessageContaining("The 'customer' and 'domain' can not be in the same query");
    }

    @Test
    @DisplayName("Test AndFilter with three search attributes")
    public void testAndFilterThreeAttributes() {
        // Test that AND filters with three search attributes are properly translated
        // to space-separated query clauses

        // Setup minimal mock response expecting space-separated query
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("givenName=John familyName=Doe email=john.doe@example.com"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"users\": []}")));

        // Create AndFilter with three attributes - ConnID processes as nested: And(And(A, B), C)
        Filter filter = FilterBuilder.and(
                FilterBuilder.equalTo(AttributeBuilder.build("givenName", "John")),
                FilterBuilder.equalTo(AttributeBuilder.build("familyName", "Doe")),
                FilterBuilder.equalTo(AttributeBuilder.build("email", "john.doe@example.com"))
        );

        // Execute search with filter
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Verify request was sent with space-separated query (implicit AND)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("givenName=John familyName=Doe email=john.doe@example.com")));
    }

    @Test
    @DisplayName("Test conflicting customer and domain with additional search filter (should throw exception)")
    public void testConflictingCustomerDomainWithSearchFilter() {
        // Test that customer/domain conflict is detected even with additional search conditions
        // The conflict should be detected during the processing of the nested AND structure

        Filter filter = FilterBuilder.and(
                FilterBuilder.equalTo(AttributeBuilder.build("customer", "C01abc123")),
                FilterBuilder.equalTo(AttributeBuilder.build("domain", "example.com")),
                FilterBuilder.equalTo(AttributeBuilder.build("givenName", "John"))
        );

        assertThatThrownBy(() -> {
            connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);
        })
                .isInstanceOf(InvalidAttributeValueException.class)
                .hasMessageContaining("The 'customer' and 'domain' can not be in the same query");
    }

    @Test
    @DisplayName("Test AndFilter with UID priority over multiple search filters")
    public void testAndFilterUidPriorityMultiple() {
        // Test that UID takes precedence even when combined with multiple search filters
        // GoogleFilterTranslator should return UID filter, ignoring all other conditions

        // Setup minimal mock response for read-by-id operation
        stubFor(get(urlPathEqualTo("/admin/directory/v1/users/user456"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": \"user456\", \"primaryEmail\": \"user456@example.com\"}")));

        // Create AndFilter with UID + multiple search filters
        Filter filter = FilterBuilder.and(
                FilterBuilder.equalTo(new Uid("user456")),
                FilterBuilder.equalTo(AttributeBuilder.build("givenName", "John")),
                FilterBuilder.equalTo(AttributeBuilder.build("familyName", "Doe"))
        );

        // Execute search with filter
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Should process as read-by-id since UID takes precedence (ignoring search filters)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users/user456")));
    }

    // ========== QUERY ESCAPING TESTS ==========

    @Test
    @DisplayName("Test filter with spaces (should be quoted and escaped)")
    public void testFilterWithSpaces() {
        // Setup minimal mock response
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("givenName='John David'"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"users\": []}")));

        // Create EqualsFilter with spaces in value
        Filter filter = FilterBuilder.equalTo(AttributeBuilder.build("givenName", "John David"));

        // Execute search with filter
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Verify query is properly quoted
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("givenName='John David'")));
    }

    @Test
    @DisplayName("Test filter with single quotes (should be escaped)")
    public void testFilterWithSingleQuotes() {
        // Setup minimal mock response (URL encoding changes John's to John%27s, handle both)
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("query", matching("givenName.*John.*s"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"users\": []}")));

        // Create EqualsFilter with single quotes in value
        Filter filter = FilterBuilder.equalTo(AttributeBuilder.build("givenName", "John's"));

        // Execute search with filter
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Verify query parameter contains the search term (may be URL encoded)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("query", matching(".*givenName.*John.*s.*")));
    }

    // ========== URL ENCODING TESTS ==========
    //
    // Google API Client SDK automatically handles URL encoding for query parameters.
    // Special characters are correctly encoded in HTTP requests:
    //   - & (ampersand) → %26
    //   - = (equals)    → %3D  
    //   - % (percent)   → %25
    //   - + (plus)      → %2B
    //   - # (hash)      → %23
    //
    // No additional URL encoding is required in the connector implementation.
    // The existing GoogleFilter.STRING_ESCAPER (for single quotes) is sufficient.
    //

    @Test
    @DisplayName("Test filter with ampersand character (URL encoding)")
    public void testFilterWithAmpersand() {
        // Setup mock response - Google API Client should URL encode & as %26
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"users\": []}")));

        // Create EqualsFilter with ampersand in value
        Filter filter = FilterBuilder.equalTo(AttributeBuilder.build("givenName", "Smith&Co"));

        // Execute search with filter
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Verify request was sent - WireMock matches decoded parameters
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("givenName=Smith&Co")));
    }

    @Test
    @DisplayName("Test filter with equals character (URL encoding)")
    public void testFilterWithEquals() {
        // Setup mock response - Google API Client should URL encode = as %3D
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("givenName=A=B"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"users\": []}")));

        // Create EqualsFilter with equals sign in value
        Filter filter = FilterBuilder.equalTo(AttributeBuilder.build("givenName", "A=B"));

        // Execute search with filter
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Verify request was sent - WireMock matches decoded parameters
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("givenName=A=B")));
    }

    @Test
    @DisplayName("Test filter with plus character (URL encoding)")
    public void testFilterWithPlus() {
        // Setup mock response - Google API Client should URL encode + as %2B
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("givenName=A+B"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"users\": []}")));

        // Create EqualsFilter with plus sign in value
        Filter filter = FilterBuilder.equalTo(AttributeBuilder.build("givenName", "A+B"));

        // Execute search with filter
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Verify request was sent - WireMock matches decoded parameters
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("givenName=A+B")));
    }

    @Test
    @DisplayName("Test filter with percent character (URL encoding)")
    public void testFilterWithPercent() {
        // Setup mock response - Google API Client should URL encode % as %25
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"users\": []}")));

        // Create EqualsFilter with percent sign in value
        Filter filter = FilterBuilder.equalTo(AttributeBuilder.build("givenName", "100%"));

        // Execute search with filter
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Verify request was sent - WireMock matches decoded parameters
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("givenName=100%")));
    }

    @Test
    @DisplayName("Test filter with hash character (URL encoding)")
    public void testFilterWithHash() {
        // Setup mock response - Google API Client should URL encode # as %23
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("givenName=Team#1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"users\": []}")));

        // Create EqualsFilter with hash character in value
        Filter filter = FilterBuilder.equalTo(AttributeBuilder.build("givenName", "Team#1"));

        // Execute search with filter
        connectorFacade.search(ObjectClass.ACCOUNT, filter, obj -> true, null);

        // Verify request was sent - WireMock matches decoded parameters
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("query", equalTo("givenName=Team#1")));
    }
}
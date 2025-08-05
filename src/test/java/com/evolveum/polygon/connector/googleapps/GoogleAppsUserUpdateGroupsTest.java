package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Tests for GoogleAppsConnector User groups attribute update operations
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsUserUpdateGroupsTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Update user - add single group")
    public void testUpdateUserAddSingleGroup() {
        // Setup mock for group membership addition
        stubFor(post(urlPathEqualTo("/admin/directory/v1/groups/group003@example.com/members"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"user004\",\n" +
                                "  \"email\": \"user004@example.com\",\n" +
                                "  \"role\": \"MEMBER\",\n" +
                                "  \"etag\": \"\\\"member004-etag\\\"\"\n" +
                                "}")));

        // Prepare modifications - Add operation
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("__GROUPS__",
                Arrays.asList("group003@example.com"), // valuesToAdd
                null)); // valuesToRemove

        // Update user
        connectorFacade.updateDelta(ObjectClass.ACCOUNT, new Uid("user004"), modifications, null);

        // Verify API call
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group003@example.com/members"))
                .withRequestBody(containing("\"id\":\"user004\""))
                .withRequestBody(containing("\"role\":\"MEMBER\"")));
    }

    @Test
    @DisplayName("Update user - add multiple groups")
    public void testUpdateUserAddMultipleGroups() {
        // Setup mock for first group membership addition
        stubFor(post(urlPathEqualTo("/admin/directory/v1/groups/group004@example.com/members"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"user005\",\n" +
                                "  \"email\": \"user005@example.com\",\n" +
                                "  \"role\": \"MEMBER\",\n" +
                                "  \"etag\": \"\\\"member005-etag\\\"\"\n" +
                                "}")));

        // Setup mock for second group membership addition
        stubFor(post(urlPathEqualTo("/admin/directory/v1/groups/group005@example.com/members"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"user005\",\n" +
                                "  \"email\": \"user005@example.com\",\n" +
                                "  \"role\": \"MEMBER\",\n" +
                                "  \"etag\": \"\\\"member006-etag\\\"\"\n" +
                                "}")));

        // Prepare modifications - Add multiple groups
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("__GROUPS__",
                Arrays.asList("group004@example.com", "group005@example.com"), // valuesToAdd
                null)); // valuesToRemove

        // Update user
        connectorFacade.updateDelta(ObjectClass.ACCOUNT, new Uid("user005"), modifications, null);

        // Verify API calls
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group004@example.com/members")));
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group005@example.com/members")));
    }

    @Test
    @DisplayName("Update user - remove single group")
    public void testUpdateUserRemoveSingleGroup() {
        // Setup mock for group membership removal
        stubFor(delete(urlPathEqualTo("/admin/directory/v1/groups/group006@example.com/members/user006"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Prepare modifications - Remove operation
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("__GROUPS__",
                null, // valuesToAdd
                Arrays.asList("group006@example.com"))); // valuesToRemove

        // Update user
        connectorFacade.updateDelta(ObjectClass.ACCOUNT, new Uid("user006"), modifications, null);

        // Verify API call
        GoogleApiMockServer.verifyRequest(deleteRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group006@example.com/members/user006")));
    }

    @Test
    @DisplayName("Update user - remove multiple groups")
    public void testUpdateUserRemoveMultipleGroups() {
        // Setup mock for first group membership removal
        stubFor(delete(urlPathEqualTo("/admin/directory/v1/groups/group007@example.com/members/user007"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Setup mock for second group membership removal
        stubFor(delete(urlPathEqualTo("/admin/directory/v1/groups/group008@example.com/members/user007"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Prepare modifications - Remove multiple groups
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("__GROUPS__",
                null, // valuesToAdd
                Arrays.asList("group007@example.com", "group008@example.com"))); // valuesToRemove

        // Update user
        connectorFacade.updateDelta(ObjectClass.ACCOUNT, new Uid("user007"), modifications, null);

        // Verify API calls
        GoogleApiMockServer.verifyRequest(deleteRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group007@example.com/members/user007")));
        GoogleApiMockServer.verifyRequest(deleteRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group008@example.com/members/user007")));
    }

    @Test
    @DisplayName("Update user - mixed add and remove operations")
    public void testUpdateUserMixedAddRemoveGroups() {
        // Setup mock for group membership addition
        stubFor(post(urlPathEqualTo("/admin/directory/v1/groups/group009@example.com/members"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"user008\",\n" +
                                "  \"email\": \"user008@example.com\",\n" +
                                "  \"role\": \"MEMBER\",\n" +
                                "  \"etag\": \"\\\"member008-etag\\\"\"\n" +
                                "}")));

        // Setup mock for group membership removal
        stubFor(delete(urlPathEqualTo("/admin/directory/v1/groups/group010@example.com/members/user008"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Prepare modifications - Mixed Add/Remove operations
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("__GROUPS__",
                Arrays.asList("group009@example.com"), // valuesToAdd
                Arrays.asList("group010@example.com"))); // valuesToRemove

        // Update user
        connectorFacade.updateDelta(ObjectClass.ACCOUNT, new Uid("user008"), modifications, null);

        // Verify API calls
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group009@example.com/members")));
        GoogleApiMockServer.verifyRequest(deleteRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group010@example.com/members/user008")));
    }

    @Test
    @DisplayName("Update user - replace groups operation")
    public void testUpdateUserReplaceGroups() {
        // Setup mock for getting current groups (user009 is currently in group010@example.com and group013@example.com)
        stubFor(get(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("userKey", equalTo("user009"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [\n" +
                                "    {\n" +
                                "      \"id\": \"group010\",\n" +
                                "      \"email\": \"group010@example.com\"\n" +
                                "    },\n" +
                                "    {\n" +
                                "      \"id\": \"group013\",\n" +
                                "      \"email\": \"group013@example.com\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // Setup mock for removing user from group010 (user should be removed from this group)
        stubFor(delete(urlPathEqualTo("/admin/directory/v1/groups/group010/members/user009"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Setup mock for removing user from group013 (user should be removed from this group)
        stubFor(delete(urlPathEqualTo("/admin/directory/v1/groups/group013/members/user009"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Setup mock for adding user to group011 (new group)
        stubFor(post(urlPathEqualTo("/admin/directory/v1/groups/group011/members"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"user009\",\n" +
                                "  \"email\": \"user009@example.com\",\n" +
                                "  \"role\": \"MEMBER\",\n" +
                                "  \"etag\": \"\\\"member011-etag\\\"\"\n" +
                                "}")));

        // Setup mock for adding user to group012 (new group)
        stubFor(post(urlPathEqualTo("/admin/directory/v1/groups/group012/members"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"user009\",\n" +
                                "  \"email\": \"user009@example.com\",\n" +
                                "  \"role\": \"MEMBER\",\n" +
                                "  \"etag\": \"\\\"member012-etag\\\"\"\n" +
                                "}")));

        // Prepare modifications - Replace operation
        Set<AttributeDelta> modifications = new HashSet<>();
        AttributeDelta replaceGroupsDelta = AttributeDeltaBuilder.build("__GROUPS__",
                Arrays.asList("group011", "group012"));
        modifications.add(replaceGroupsDelta);

        // Update user - this should now work properly
        connectorFacade.updateDelta(ObjectClass.ACCOUNT, new Uid("user009"), modifications, null);

        // Verify API calls were made correctly
        // 1. Get current groups
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("userKey", equalTo("user009")));

        // 2. Remove from old groups
        GoogleApiMockServer.verifyRequest(deleteRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group010/members/user009")));
        GoogleApiMockServer.verifyRequest(deleteRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group013/members/user009")));

        // 3. Add to new groups
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group011/members")));
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group012/members")));
    }

    @Test
    @DisplayName("Update user - add to non-existent group should handle error")
    public void testUpdateUserAddToNonExistentGroup() {
        // Setup mock for group membership addition - return 404 Not Found
        stubFor(post(urlPathEqualTo("/admin/directory/v1/groups/nonexistent@example.com/members"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"error\": {\n" +
                                "    \"code\": 404,\n" +
                                "    \"message\": \"Group not found\"\n" +
                                "  }\n" +
                                "}")));

        // Prepare modifications - Add operation to non-existent group
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("__GROUPS__",
                Arrays.asList("nonexistent@example.com"), // valuesToAdd
                null)); // valuesToRemove

        // Update user - should not throw exception (error is logged and ignored)
        connectorFacade.updateDelta(ObjectClass.ACCOUNT, new Uid("user010"), modifications, null);

        // Verify API call was attempted
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/nonexistent@example.com/members")));
    }

    @Test
    @DisplayName("Update user - remove from non-existent group should handle error")
    public void testUpdateUserRemoveFromNonExistentGroup() {
        // Setup mock for group membership removal - return 404 Not Found
        stubFor(delete(urlPathEqualTo("/admin/directory/v1/groups/nonexistent@example.com/members/user011"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"error\": {\n" +
                                "    \"code\": 404,\n" +
                                "    \"message\": \"Group or member not found\"\n" +
                                "  }\n" +
                                "}")));

        // Prepare modifications - Remove operation from non-existent group
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("__GROUPS__",
                null, // valuesToAdd
                Arrays.asList("nonexistent@example.com"))); // valuesToRemove

        // Update user - should not throw exception (error is logged and ignored)
        connectorFacade.updateDelta(ObjectClass.ACCOUNT, new Uid("user011"), modifications, null);

        // Verify API call was attempted
        GoogleApiMockServer.verifyRequest(deleteRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/nonexistent@example.com/members/user011")));
    }
}
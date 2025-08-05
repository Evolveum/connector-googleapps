package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GoogleAppsConnector User photo attribute update operations
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsUserUpdatePhotoTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Update photo - Add new photo")
    public void testUpdatePhotoNew() {
        // Setup mock for photo upload
        stubFor(put(urlPathEqualTo("/admin/directory/v1/users/photo002/photos/thumbnail"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"photo002\",\n" +
                                "  \"etag\": \"\\\"photo002-photo-etag\\\"\"\n" +
                                "}")));

        // Prepare delta modifications - replace operation for photo
        Set<AttributeDelta> modifications = new HashSet<>();
        byte[] newPhotoData = "new photo data for user".getBytes();
        modifications.add(AttributeDeltaBuilder.build("__PHOTO__", newPhotoData));

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("photo002"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify photo upload request
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathEqualTo("/admin/directory/v1/users/photo002/photos/thumbnail"))
                .withRequestBody(containing("\"photoData\"")));
    }

    @Test
    @DisplayName("Update photo - Replace existing photo")
    public void testUpdatePhotoReplace() {
        // Setup mock for photo upload (same as new, Google API handles it the same way)
        stubFor(put(urlPathEqualTo("/admin/directory/v1/users/photo003/photos/thumbnail"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"photo003\",\n" +
                                "  \"etag\": \"\\\"photo003-photo-etag\\\"\"\n" +
                                "}")));

        // Prepare delta modifications - replace operation
        Set<AttributeDelta> modifications = new HashSet<>();
        byte[] replacementPhotoData = "replacement photo data".getBytes();
        modifications.add(AttributeDeltaBuilder.build("__PHOTO__", replacementPhotoData));

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("photo003"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify photo upload request
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathEqualTo("/admin/directory/v1/users/photo003/photos/thumbnail"))
                .withRequestBody(containing("\"photoData\"")));
    }

    @Test
    @DisplayName("Update photo - Add operation (single-valued attribute)")
    public void testUpdatePhotoAdd() {
        // Setup mock for photo upload
        stubFor(put(urlPathEqualTo("/admin/directory/v1/users/photo004/photos/thumbnail"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"photo004\",\n" +
                                "  \"etag\": \"\\\"photo004-photo-etag\\\"\"\n" +
                                "}")));

        // Prepare delta modifications - add operation for single-valued attribute
        Set<AttributeDelta> modifications = new HashSet<>();
        byte[] addedPhotoData = "added photo data".getBytes();
        modifications.add(AttributeDeltaBuilder.build("__PHOTO__",
                Arrays.asList(addedPhotoData), // valuesToAdd
                null)); // valuesToRemove

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("photo004"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify photo upload request
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathEqualTo("/admin/directory/v1/users/photo004/photos/thumbnail"))
                .withRequestBody(containing("\"photoData\"")));
    }

    @Test
    @DisplayName("Delete photo with empty byte array")
    public void testUpdatePhotoWithEmptyData() {
        // Setup mock for photo deletion
        stubFor(delete(urlPathEqualTo("/admin/directory/v1/users/photo005/photos/thumbnail"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("")));

        // Test with empty photo data - should trigger deletion
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("__PHOTO__", new byte[0]));

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("photo005"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify photo deletion request was sent
        GoogleApiMockServer.verifyRequest(deleteRequestedFor(urlPathEqualTo("/admin/directory/v1/users/photo005/photos/thumbnail")));

        // Verify no photo upload request was made
        GoogleApiMockServer.verify(0, putRequestedFor(urlPathEqualTo("/admin/directory/v1/users/photo005/photos/thumbnail")));
    }

    @Test
    @DisplayName("Update photo with other attributes")
    public void testUpdatePhotoWithOtherAttributes() {
        // Setup mock for user update
        stubFor(put(urlPathEqualTo("/admin/directory/v1/users/photo006"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"photo006\",\n" +
                                "  \"etag\": \"\\\"photo006-updated-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"photo006@example.com\"\n" +
                                "}")));

        // Setup mock for photo upload
        stubFor(put(urlPathEqualTo("/admin/directory/v1/users/photo006/photos/thumbnail"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"photo006\",\n" +
                                "  \"etag\": \"\\\"photo006-photo-etag\\\"\"\n" +
                                "}")));

        // Prepare delta modifications - photo and regular attributes
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("givenName", "Updated"));
        modifications.add(AttributeDeltaBuilder.build("familyName", "PhotoUser"));

        byte[] photoData = "updated photo with other attrs".getBytes();
        modifications.add(AttributeDeltaBuilder.build("__PHOTO__", photoData));

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("photo006"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify user update request
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathEqualTo("/admin/directory/v1/users/photo006"))
                .withRequestBody(containing("\"givenName\":\"Updated\""))
                .withRequestBody(containing("\"familyName\":\"PhotoUser\"")));

        // Verify photo upload request
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathEqualTo("/admin/directory/v1/users/photo006/photos/thumbnail"))
                .withRequestBody(containing("\"photoData\"")));
    }

    @Test
    @DisplayName("Delete user photo with empty replace")
    public void testDeleteUserPhoto() {
        // Setup mock for photo deletion
        stubFor(delete(urlPathEqualTo("/admin/directory/v1/users/photo007/photos/thumbnail"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("")));

        // Prepare update operation with empty photo data (deletion)
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("__PHOTO__", Collections.emptyList()));

        // Execute update to delete photo
        connectorFacade.updateDelta(ObjectClass.ACCOUNT, new Uid("photo007"), modifications, null);

        // Verify photo deletion request was sent
        GoogleApiMockServer.verifyRequest(deleteRequestedFor(urlPathEqualTo("/admin/directory/v1/users/photo007/photos/thumbnail")));

        // Verify no core user update was sent (photo-only operation)
        GoogleApiMockServer.verify(0, putRequestedFor(urlPathEqualTo("/admin/directory/v1/users/photo007")));
    }

    @Test
    @DisplayName("Delete user photo with null value in replace")
    public void testDeleteUserPhotoWithNullValue() {
        // Setup mock for photo deletion
        stubFor(delete(urlPathEqualTo("/admin/directory/v1/users/photo008/photos/thumbnail"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("")));

        // Prepare update operation with null photo data (deletion)
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("__PHOTO__", Arrays.asList((byte[]) null)));

        // Execute update to delete photo
        connectorFacade.updateDelta(ObjectClass.ACCOUNT, new Uid("photo008"), modifications, null);

        // Verify photo deletion request was sent
        GoogleApiMockServer.verifyRequest(deleteRequestedFor(urlPathEqualTo("/admin/directory/v1/users/photo008/photos/thumbnail")));

        // Verify no core user update was sent (photo-only operation)
        GoogleApiMockServer.verify(0, putRequestedFor(urlPathEqualTo("/admin/directory/v1/users/photo008")));
    }
}
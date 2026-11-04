package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

public class GroupProjectionTest extends GoogleAppsConnectorTestBase {

    @Test
    public void testMemberFlags() {
        // Given: Group members attribute name
        String attributeName = GoogleAppsConstants.MEMBERS_ATTR;

        // When: Read group members metadata
        ObjectClassInfo group = connectorFacade.schema().findObjectClassInfo(ObjectClass.GROUP_NAME);
        AttributeInfo members = group.getAttributeInfo().stream().filter(attribute -> attributeName.equals(attribute.getName())).findFirst().orElseThrow(AssertionError::new);

        // Then: Verify optional read-only membership
        assertThat(members.isMultiValued()).isTrue();
        assertThat(members.isReturnedByDefault()).isFalse();
        assertThat(members.isCreateable()).isFalse();
        assertThat(members.isUpdateable()).isFalse();
    }

    @Test
    public void testDefaultGroup() {
        // Given: Group without requested membership
        stubGroup();
        List<ConnectorObject> groups = new ArrayList<>();

        // When: Read default group attributes
        connectorFacade.search(ObjectClass.GROUP, FilterBuilder.equalTo(new Uid("group001")), groups::add, null);

        // Then: Return group without membership requests
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getUid().getUidValue()).isEqualTo("group001");
        assertThat(groups.get(0).getName().getNameValue()).isEqualTo("group@example.com");
        assertThat(groups.get(0).getAttributeByName(GoogleAppsConstants.MEMBERS_ATTR)).isNull();
        GoogleApiMockServer.verify(1, getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group001")));
        GoogleApiMockServer.verify(0, getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group001/members")));
    }

    @Test
    public void testExplicitMembers() {
        // Given: Explicitly requested group membership
        stubGroup();
        stubFor(get(urlPathEqualTo("/admin/directory/v1/groups/group001/members"))
                .willReturn(okJson("{\"members\":[{\"id\":\"user001\",\"email\":\"user@example.com\",\"role\":\"MEMBER\",\"type\":\"USER\"}]}")));
        OperationOptions options = new OperationOptionsBuilder().setAttributesToGet(GoogleAppsConstants.MEMBERS_ATTR).build();
        List<ConnectorObject> groups = new ArrayList<>();

        // When: Read group with requested membership
        connectorFacade.search(ObjectClass.GROUP, FilterBuilder.equalTo(new Uid("group001")), groups::add, options);

        // Then: Return complete requested membership
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getUid().getUidValue()).isEqualTo("group001");
        Attribute members = groups.get(0).getAttributeByName(GoogleAppsConstants.MEMBERS_ATTR);
        assertThat(members.getValue()).containsExactly("user@example.com");
        assertThat(members.getAttributeValueCompleteness()).isEqualTo(AttributeValueCompleteness.COMPLETE);
        GoogleApiMockServer.verify(1, getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group001/members")));
    }

    private void stubGroup() {
        stubFor(get(urlPathEqualTo("/admin/directory/v1/groups/group001"))
                .willReturn(okJson("{\"id\":\"group001\",\"email\":\"group@example.com\",\"name\":\"Example Group\"}")));
    }
}

package com.evolveum.polygon.connector.googleapps;

import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.services.directory.Directory;
import com.google.api.services.directory.model.User;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributesAccessor;
import org.identityconnectors.framework.common.objects.Name;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ActivationMappingTest {
    @Test public void disabledAccountIsSuspendedOnCreate() throws Exception {
        Set<Attribute> attrs=new HashSet<>(Arrays.asList(new Name("user@example.com"),
            AttributeBuilder.build("givenName","Test"), AttributeBuilder.build("familyName","User"),
            AttributeBuilder.buildPassword(new GuardedString("secret".toCharArray())),AttributeBuilder.buildEnabled(false)));
        Directory d=new Directory.Builder(new MockHttpTransport(),new GsonFactory(),null).setApplicationName("test").build();
        User user=(User)UserHandler.createUser(d.users(),new AttributesAccessor(attrs)).getJsonContent();
        assertThat(user.getSuspended()).isTrue();
    }
    @Test public void activationProjectionRequestsSuspendedField() {
        String fields=new SchemaDefinition(UserHandler.getUserClassInfo()).createGoogleApiFieldsString(
            new HashSet<>(Collections.singletonList("__ENABLE__")),"id");
        assertThat(fields).contains("suspended");
    }
}

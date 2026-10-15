package com.evolveum.polygon.connector.googleapps;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.directory.Directory;
import com.google.api.services.directory.model.User;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.*;
import org.testng.annotations.Test;
import java.util.*;
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
        String fields=new GoogleAppsConnector().getFields(ObjectClass.ACCOUNT,
            new OperationOptionsBuilder().setAttributesToGet("__ENABLE__").build(),"id");
        assertThat(fields).contains("suspended");
    }
}

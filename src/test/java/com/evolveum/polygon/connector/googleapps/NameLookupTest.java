package com.evolveum.polygon.connector.googleapps;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.directory.Directory;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class NameLookupTest {
    @Test public void accountNameUsesGetEndpoint() { verify(ObjectClass.ACCOUNT,"users","primaryEmail"); }
    @Test public void groupNameUsesGetEndpoint() { verify(ObjectClass.GROUP,"groups","email"); }
    private void verify(ObjectClass type,String collection,String emailField) {
        List<String> urls=new ArrayList<>();
        MockHttpTransport transport=new MockHttpTransport(){
            @Override public LowLevelHttpRequest buildRequest(String method,String url) throws IOException {
                urls.add(url); return new MockLowLevelHttpRequest(url).setResponse(new MockLowLevelHttpResponse()
                    .setContentType("application/json").setContent("{\"id\":\"immutable-id\",\""+emailField+"\":\"user@example.com\"}"));
            }
        };
        GoogleAppsConfiguration config=new GoogleAppsConfiguration(){
            @Override public Directory getDirectory(){return new Directory.Builder(transport,new GsonFactory(),null).setApplicationName("test").build();}
        };
        config.setDomain("example.com");config.setAllowCache(false);
        GoogleAppsConnector c=new GoogleAppsConnector();c.init(config);
        List<ConnectorObject> results=new ArrayList<>();
        c.executeQuery(type,c.createFilterTranslator(type, new OperationOptionsBuilder().build()).translate(FilterBuilder.equalTo(new Name("user@example.com"))).get(0),o->{results.add(o);return true;},
                new OperationOptionsBuilder().setAttributesToGet("__NAME__").build());
        assertThat(urls).hasSize(1);assertThat(urls.get(0)).contains("/"+collection+"/user@example.com");
        assertThat(results).hasSize(1);assertThat(results.get(0).getUid().getUidValue()).isEqualTo("immutable-id");
    }
}

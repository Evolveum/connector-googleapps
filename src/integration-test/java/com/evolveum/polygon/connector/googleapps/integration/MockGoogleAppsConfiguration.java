package com.evolveum.polygon.connector.googleapps.integration;

import com.evolveum.polygon.connector.googleapps.GoogleAppsConfiguration;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.directory.Directory;
import com.google.api.services.licensing.Licensing;

import java.io.IOException;
import java.net.Proxy;

/** Test-only configuration: actual HTTP and SDK serialization, without OAuth or external endpoints. */
final class MockGoogleAppsConfiguration extends GoogleAppsConfiguration {
    private final Directory directory;

    MockGoogleAppsConfiguration(GoogleDirectoryMockServer server) {
        String root = server.rootUrl();
        directory = new Directory.Builder(new NetHttpTransport.Builder().setProxy(Proxy.NO_PROXY).build(),
                GsonFactory.getDefaultInstance(), request -> {
                    if (!request.getUrl().build().startsWith(root)) {
                        throw new IOException("Mock mode refuses requests outside its local HTTP server");
                    }
                    request.setConnectTimeout(2000);
                    request.setReadTimeout(3000);
                    request.setFollowRedirects(false);
                })
                .setRootUrl(root)
                .setApplicationName("connector-googleapps-mock-it")
                .build();
        setDomain("mock.example.test");
        setProductId("mock-product");
        setSkuId("mock-sku");
        setClientId("mock-client");
        setAutoAddLicense(false);
        setAllowCache(false);
        validate();
    }

    @Override
    public Directory getDirectory() {
        return directory;
    }

    @Override
    public Licensing getLicensing() {
        throw new UnsupportedOperationException("The local POC does not emulate licensing");
    }

    @Override
    public void getGoogleCredential() {
        throw new UnsupportedOperationException("Mock mode must never request Google credentials");
    }
}

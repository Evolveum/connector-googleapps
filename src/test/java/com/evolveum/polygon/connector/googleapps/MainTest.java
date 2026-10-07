/*
 * Copyright (c) 2026 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.polygon.connector.googleapps;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.jspecify.annotations.NonNull;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Scanner;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class MainTest {

    @Test
    public void mainWritesClientSecretsAndDisplaysConfigurationWithoutStartingOAuth() throws Exception {
        Path tempDirectory = Files.createTempDirectory("googleapps-main-");
        File previousFile = Main.clientSecretsFile;
        Main.ConfigurationMapProvider previousProvider = Main.configurationMapProvider;
        Main.WriterFactory previousWriterFactory = Main.writerFactory;
        java.io.InputStream previousIn = System.in;
        PrintStream previousOut = System.out;
        PrintStream previousErr = System.err;
        File secrets = tempDirectory.resolve("client_secrets.json").toFile();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try {
            Main.clientSecretsFile = secrets;
            Main.configurationMapProvider = file -> Main.configurationMap("client-id", "client-secret", "refresh-token");
            Main.writerFactory = java.io.FileWriter::new;
            System.setIn(new ByteArrayInputStream("client-id client-secret".getBytes(StandardCharsets.UTF_8)));
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

            Main.main(new String[0]);

            assertTrue(secrets.isFile());
            JsonObject installed = JsonParser.parseString(Files.readString(secrets.toPath())).getAsJsonObject()
                    .getAsJsonObject("installed");
            assertEquals(installed.get("client_id").getAsString(), "client-id");
            assertEquals(installed.get("client_secret").getAsString(), "client-secret");
            assertTrue(output.toString(StandardCharsets.UTF_8).contains("Generated credentials:"));
            assertTrue(output.toString(StandardCharsets.UTF_8).contains("refresh-token"));
        } finally {
            Main.clientSecretsFile = previousFile;
            Main.configurationMapProvider = previousProvider;
            Main.writerFactory = previousWriterFactory;
            System.setIn(previousIn);
            System.setOut(previousOut);
            System.setErr(previousErr);
            Files.deleteIfExists(secrets.toPath());
            Files.deleteIfExists(tempDirectory);
        }
    }

    @Test
    public void cliReportsWriteFailureClosesWriterAndShowsUsageForMissingFile() throws Exception {
        File missingFile = new File("missing-client-secrets.json") {
            @Override
            public boolean exists() {
                return false;
            }

            @Override
            public boolean isFile() {
                return false;
            }
        };
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        try (TrackingFailingWriter writer = new TrackingFailingWriter()) {

            Main.run(new String[0], new Scanner("client-id client-secret"),
                    new PrintStream(output, true, StandardCharsets.UTF_8),
                    new PrintStream(error, true, StandardCharsets.UTF_8), missingFile,
                    file -> {
                        throw new AssertionError("OAuth provider must not be called for a missing file");
                    },
                    file -> writer);

            assertTrue(writer.closed);
        }
        assertTrue(error.toString(StandardCharsets.UTF_8).contains("simulated write failure"));
        assertTrue(error.toString(StandardCharsets.UTF_8).contains("Invalid client secret path"));
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Usage: java -jar"));
    }

    @Test
    public void configurationMapContainsExactlyTheThreeConnectorCredentials() {
        Map<String, Object> configuration = Main.configurationMap("id", "secret", "refresh");

        assertEquals(configuration.size(), 3);
        assertEquals(configuration.get("clientId"), "id");
        assertEquals(configuration.get("clientSecret"), "secret");
        assertEquals(configuration.get("refreshToken"), "refresh");
        assertFalse(configuration.containsKey("accessToken"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void invalidClientSecretsFailBeforeOAuthAuthorizationStarts() throws Exception {
        Path file = Files.createTempFile("googleapps-client-secrets-", ".json");
        String previousProtocols = System.getProperty("https.protocols");
        try {
            Files.writeString(file, "{");
            Main.getConfigurationMap(file.toFile());
        } finally {
            if (previousProtocols == null) {
                System.clearProperty("https.protocols");
            } else {
                System.setProperty("https.protocols", previousProtocols);
            }
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void configurationMapLoadsClientSecretsAndUsesTheReturnedRefreshToken() throws Exception {
        Path file = Files.createTempFile("googleapps-client-secrets-", ".json");
        String previousProtocols = System.getProperty("https.protocols");
        Main.CredentialAuthorizer previousAuthorizer = Main.credentialAuthorizer;
        try {
            Files.writeString(file, "{\"installed\":{\"client_id\":\"client-id\","
                    + "\"client_secret\":\"client-secret\","
                    + "\"auth_uri\":\"https://example.test/auth\","
                    + "\"token_uri\":\"https://example.test/token\"}}");
            Main.credentialAuthorizer = flow -> {
                assertEquals(flow.getAccessType(), "offline");
                assertEquals(flow.getApprovalPrompt(), "force");
                assertEquals(flow.getScopes().size(), 4);
                return new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
                        .setTransport(new NetHttpTransport())
                        .setJsonFactory(new GsonFactory())
                        .setClientAuthentication(new ClientParametersAuthentication("client-id", "client-secret"))
                        .setTokenServerUrl(new GenericUrl("https://example.test/token"))
                        .build().setRefreshToken("refresh-token");
            };

            Map<String, Object> result = Main.getConfigurationMap(file.toFile());

            assertEquals(result.get("clientId"), "client-id");
            assertEquals(result.get("clientSecret"), "client-secret");
            assertEquals(result.get("refreshToken"), "refresh-token");
        } finally {
            Main.credentialAuthorizer = previousAuthorizer;
            if (previousProtocols == null) {
                System.clearProperty("https.protocols");
            } else {
                System.setProperty("https.protocols", previousProtocols);
            }
            Files.deleteIfExists(file);
        }
    }

    private static final class TrackingFailingWriter extends Writer {
        private boolean closed;

        @Override
        public void write(char @NonNull [] buffer, int offset, int length) throws IOException {
            throw new IOException("simulated write failure");
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}

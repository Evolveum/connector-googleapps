Status: [![Build Status](https://travis-ci.org/Evolveum/connector-googleapps.svg?branch=master)](https://travis-ci.org/Evolveum/connector-googleapps)

## Google Apps Connector
* Doc: [Wiki](https://wiki.evolveum.com/display/midPoint/Google+Apps+Connector)

## Opt-in Google Workspace CRUD proof of concept

`UserCrudIT` creates one uniquely named temporary user, reads it by UID, updates
`givenName`, verifies the updated value and unchanged identity/family name, deletes
the user, and verifies absence. It uses the real `GoogleAppsConnector` and Google
Directory SDK, targeting either Google Workspace or a local HTTP mock server.
This is connector-level coverage, not a midPoint deployment test.

### Local mock server: no Google account or credentials needed

```shell
mvn -Pmock-integration-tests verify
```

Run unit tests and the mock integration test together:

```shell
mvn -Pall-tests -Dgws.it.target=mock verify
```

Both commands execute the same `UserCrudIT` lifecycle against a stateful HTTP server
started on `127.0.0.1` with an automatically assigned port. The server is stopped
in teardown, including after a test failure. It uses the JDK HTTP server and the
existing Google SDK/TestNG libraries; no Docker, new dependency or manual server
startup is required. `mvn -o` also works once Maven dependencies are cached.

Mock mode does not read `gws-it.local.properties` or environment credentials, and
does not request OAuth tokens. Its test-only configuration uses synthetic values
and a real HTTP transport pointed at the local server, with proxies and redirects
disabled. The normal production connector configuration is unchanged.

The mock implements the users endpoints needed by the POC: create, get by ID/email,
patch name, and delete, including JSON, etags and 404/409 responses. It keeps user
state in memory, discards passwords, and supports the SDK's PATCH-over-POST and gzip
request encoding. The lifecycle test also asserts the HTTP request sequence and
that deletion leaves no user on the server.

This fixture checks connector/SDK serialization, HTTP routing, attribute mapping
and CRUD behavior. It does not validate Google OAuth, permissions, quotas, eventual
consistency, licensing or full API compatibility; field masks are not emulated.
The existing live profiles remain available for those environment checks.

TestNG remains the test framework. Surefire runs unit tests and Failsafe runs
integration tests; build-helper registers the integration source/resource directories.
No application or test-library dependencies are added.

```text
src/
  main/java/                 production code
  main/resources/            production resources
  test/java/                 *Test.java unit tests and shared test helpers
  test/resources/            unit/shared test resources
  integration-test/java/     *IT.java integration tests and their base classes
  integration-test/resources/  optional integration-only resources
```

`IntegrationConfigurationTest` and its helper `IntegrationConfiguration` stay under
`src/test/java`; the loader is shared with integration tests, like Gradle's
`integrationTest.compileClasspath += main.output + test.output`. `UserCrudIT` and
`GoogleAppsIntegrationSupport` live under `src/integration-test/java`.
The integration source/resource directories are registered only when `skipITs=false`.
Integration mode compiles shared/unit test sources too, but runs only `*IT.java`.
Helper classes do not need a test suffix; executable tests must use `*Test` or `*IT`.

Ordinary builds use the `unit-tests` profile. Explicitly selecting `integration-tests`
or `all-tests` also sets `gws.it.enabled=true`: by default these profiles run real
Google CRUD operations using the local configuration. No extra enable flag is needed.
`gws.it.target=mock` selects the local fixture instead; the live enable flag only
applies to `gws.it.target=live` (the default). Other target values fail setup.
Run sequentially because the production connector uses a shared cache; the POC
explicitly disables that cache. It polls reads for up to 120 seconds per state,
with a fresh result collector every two seconds. API/authentication errors fail
immediately; successful creates and entire tests are never blindly retried.

### Supply configuration locally (live mode only)

From the repository root, copy the empty example once, then edit the local copy:

```powershell
Copy-Item gws-it.example.properties gws-it.local.properties
```

`gws-it.local.properties` is ignored by Git and is deliberately outside
`src/test/resources`, so it is not copied into test artifacts. Do not paste secrets
into chat, commit them, or put them into command-line Maven properties. Alternatively,
keep credentials in environment variables injected by your local secret tooling/CI.
Environment values override the file, including empty values.

| File key | Environment override | Value |
| --- | --- | --- |
| `domain` | `GWS_IT_DOMAIN` | Workspace email domain, e.g. `test.example.com`, without `https://`, `@`, or a path. |
| `productId` | `GWS_IT_PRODUCT_ID` | Your Google licensing product ID. Required by existing connector validation, even when auto-add is false. |
| `skuId` | `GWS_IT_SKU_ID` | Your corresponding SKU ID; also required by validation. |
| `autoAddLicense` | `GWS_IT_AUTO_ADD_LICENSE` | `false` for the first run (default); `true` invokes the connector's automatic license assignment during create. |
| `clientId` | `GWS_IT_CLIENT_ID` | OAuth client ID associated with the refresh token. |
| `clientSecret` | `GWS_IT_CLIENT_SECRET` | That OAuth client's secret. |
| `refreshToken` | `GWS_IT_REFRESH_TOKEN` | Refresh token for the same OAuth client and an authorized Workspace administrator. |
| `orgUnitPath` | `GWS_IT_ORG_UNIT_PATH` | Optional existing test OU, e.g. `/ConnectorTests`; empty uses the default OU. |

There is no configurable `host` in this connector; the Google client uses its normal
API endpoints. The core POC needs the Admin SDK Directory API enabled and a refresh
token granted `https://www.googleapis.com/auth/admin.directory.user`, with an admin
allowed to manage the test users. With `autoAddLicense=true`, licensing access and
an available matching subscription are also required. The POC passes that switch
through but does not independently assert license assignment; start with `false`.
See [Directory authorization](https://developers.google.com/workspace/admin/directory/v1/guides/authorizing)
and [Google product/SKU IDs](https://developers.google.com/workspace/admin/licensing/v1/how-tos/products).

Use a dedicated test Workspace/domain and an existing test OU appropriate for its
licensing policy. Disabling connector-side license assignment does not disable
Workspace automatic licensing or guarantee that creating users is free.

## Running tests

The examples use unquoted Maven arguments without spaces, suitable for `cmd.exe`
and Maven goal fields in IDEs. Do not paste literal single quotes into these fields:
Maven may receive them as part of the argument and report `Unknown lifecycle phase`.
In PowerShell, quote the complete `-Dname=value` argument with double quotes when
needed (especially dotted property names or values containing spaces), for example:

```powershell
mvn -Pintegration-tests "-Dit.test=UserCrudIT" verify
```

For a configuration path containing spaces, quote the whole argument, for example
`"-Dgws.it.config=C:/private folder/gws-it.local.properties"` in a terminal.

### Unit tests only

```shell
mvn test
```

The default profile runs `*Test.java` through Surefire. The explicit equivalent is
`mvn -Punit-tests test`.

### Integration tests only

```shell
mvn -Pintegration-tests verify
```

Runs `*IT.java` through Failsafe and skips unit test execution. This profile enables
real Google CRUD operations and requires the local configuration described above.

For the same test without Google access or credentials, use
`mvn -Pmock-integration-tests verify` instead.

### All tests (unit + integration)

```shell
mvn -Pall-tests verify
```

Runs unit tests first, then integration tests, including real Google CRUD operations.
Use `mvn -Pall-tests -Dgws.it.target=mock verify` to run both suites locally.
Select exactly one test profile per run.

Use `verify` for integration profiles so Maven also checks the Failsafe result.
Running just `test` stops before the integration-test phase. `mvn verify` without
an integration profile still runs only unit tests.

Run a selected unit test:

```shell
mvn -Dtest=FiltersTest test
```

Run only the live POC, after filling the local file or injecting environment values:

```shell
mvn -Pintegration-tests -Dit.test=UserCrudIT verify
```

To keep the file outside the checkout, additionally pass
`-Dgws.it.config=C:/private/gws-it.local.properties`. The specified file must exist.
If dependencies are already cached, `mvn -o` can run Maven offline while the test
itself still calls Google. If your environment needs a forked compiler, add
`-Dmaven.compiler.fork=true`.

### Check test selection without calling Google

The enabled test fails setup when required settings are missing; it does not report
a successful live run with missing credentials. For a build-wiring check without
Google access, explicitly override the live guard:

```shell
mvn -Pintegration-tests -Dgws.it.enabled=false verify
mvn -Pall-tests -Dgws.it.enabled=false verify
```

With the default live target, these checks compile/discover the integration test but mark it skipped before reading
credentials or calling Google. A skipped run is not a successful live CRUD validation.
`-DskipITs=true` skips integration tests; `-DskipTests=true` skips both runners with
the inherited Surefire/Failsafe 3.5.2 versions.

The output identifies CREATE, READ, UPDATE and DELETE and the generated email.
Unit reports are in `target/surefire-reports`; integration reports are in
`target/failsafe-reports`. Use `clean` before a run if you need reports containing
only the current mode. Do not enable HTTP wire logging or publish credential files
with test reports.

Cleanup runs even after an assertion fails. If create fails after the remote user
was inserted (for example during automatic licensing), cleanup uses its exact email
and unique family-name marker. It never sweeps a domain or deletes by prefix.
Cleanup failure is reported separately by TestNG. Process termination, a network
outage, or a delayed partial creation can still prevent cleanup: use the generated
email in the report to inspect/remove that one test user manually. This small POC
does not include a persistent cleanup registry or scheduled janitor.

# anota-api-java · Official Java client for the [anota](https://anota.cloud) API

**[Léeme en español](README.es.md)** · [Interactive API reference](https://anota.cloud/developers) · [All SDKs](https://github.com/anotacloud/anota-api)

![CI](https://github.com/anotacloud/anota-api-java/actions/workflows/ci.yml/badge.svg)

Create and publish forms, edit fields and conditional logic, read and write
submissions, and wire webhooks — everything the anota REST API can do, from Java.

This is a **thin client**: it has zero runtime dependencies (just `java.net.http`
from the JDK) and every method returns the server's response as a raw JSON `String`.
Because the Java standard library ships no JSON parser, you pair it with the JSON
library you already use — Jackson, Gson, or another — to read responses and to build
the `fields`, `rules`, and `answers` arguments, which are passed as **pre-serialized
JSON strings**.

Requires Java 11 or newer.

## Install

**JitPack** (Maven). Add the JitPack repository and the dependency:

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.anotacloud</groupId>
  <artifactId>anota-api-java</artifactId>
  <version>v1.0.0</version>
</dependency>
```

Gradle:

```kotlin
repositories { maven { url = uri("https://jitpack.io") } }
dependencies { implementation("com.github.anotacloud:anota-api-java:v1.0.0") }
```

Or download the source directly:
[ZIP](https://github.com/anotacloud/anota-api-java/archive/refs/heads/main.zip) ·
[Tarball](https://github.com/anotacloud/anota-api-java/archive/refs/heads/main.tar.gz).

## Quickstart

```java
import cloud.anota.AnotaClient;

AnotaClient anota = new AnotaClient(System.getenv("ANOTA_API_KEY"));

// fields are a pre-serialized JSON array
String form = anota.createForm(
        "Contact us",
        "[{\"type\":\"text\",\"label\":\"Name\",\"required\":true}]");

// after reading the new form's id from `form` (with your JSON library):
anota.publishForm(formId);
anota.createSubmission(formId, "{\"name\":\"Ada\"}");   // answers are a JSON object string
String submissions = anota.listSubmissions(formId);      // raw JSON string
```

A complete runnable script is in [`examples/EndToEnd.java`](examples/EndToEnd.java).

## Authentication

Create an API key in your workspace at https://anota.cloud/api-keys and pass it to the
client. Keys look like `anota_sk_…` and also power the Claude MCP connector.

```java
AnotaClient anota = new AnotaClient("anota_sk_…");
// custom base URL (self-hosting / testing):
AnotaClient staging = new AnotaClient("anota_sk_…", "https://staging.anota.cloud/api/v1");
```

## All methods

Every method returns the raw JSON response body as a `String` (or `null` for an empty
2xx body) and may throw `AnotaApiError`, `java.io.IOException`, or `InterruptedException`.

| # | Method | HTTP |
|---|---|---|
| 1 | `listForms()` | `GET /forms` |
| 2 | `createForm(title, fields, description?)` | `POST /forms` |
| 3 | `getForm(formId)` | `GET /forms/{formId}` |
| 4 | `addFields(formId, fields)` | `POST /forms/{formId}/fields` |
| 5 | `editField(formId, fieldId, field)` | `PATCH /forms/{formId}/fields/{fieldId}` |
| 6 | `deleteField(formId, fieldId)` | `DELETE /forms/{formId}/fields/{fieldId}` |
| 7 | `publishForm(formId)` | `POST /forms/{formId}/publish` |
| 8 | `renameForm(formId, title)` | `PATCH /forms/{formId}` |
| 9 | `setPdfTemplate(formId, key)` | `PUT /forms/{formId}/pdf-template` |
| 10 | `deleteForm(formId)` | `DELETE /forms/{formId}` |
| 11 | `cloneForm(formId)` | `POST /forms/{formId}/clone` |
| 12 | `addLogicRules(formId, rules)` | `POST /forms/{formId}/logic-rules` |
| 13 | `editLogicRule(formId, ruleId, rule)` | `PUT /forms/{formId}/logic-rules/{ruleId}` |
| 14 | `deleteLogicRule(formId, ruleId)` | `DELETE /forms/{formId}/logic-rules/{ruleId}` |
| 15 | `listSubmissions(formId, page, pageSize, status?)` | `GET /forms/{formId}/submissions` |
| 16 | `getSubmission(submissionId)` | `GET /submissions/{submissionId}` |
| 17 | `createSubmission(formId, answers)` | `POST /forms/{formId}/submissions` |
| 18 | `setSubmissionStatus(submissionId, status)` | `PATCH /submissions/{submissionId}/status` |
| 19 | `deleteSubmission(submissionId)` | `DELETE /submissions/{submissionId}` |
| 20 | `submissionStats(formId)` | `GET /forms/{formId}/stats` |
| 21 | `listTemplates(language)` | `GET /templates?language=` |
| 22 | `createFormFromTemplate(templateId)` | `POST /forms/from-template/{templateId}` |
| 23 | `listWebhooks(formId)` | `GET /forms/{formId}/webhooks` |
| 24 | `addWebhook(formId, url)` | `POST /forms/{formId}/webhooks` |
| 25 | `deleteWebhook(formId, webhookId)` | `DELETE /forms/{formId}/webhooks/{webhookId}` |

`listSubmissions` and `listTemplates` also have convenience overloads
(`listSubmissions(formId)` defaults to page 1, page size 25, no status filter;
`listTemplates()` defaults to `es`). `createForm(title, fields)` omits the description.

### JSON string arguments

`fields`, `field`, `rules`, `rule`, and `answers` are accepted as pre-serialized JSON
strings so the client stays dependency-free:

- **fields / field**: `{type, label, required?, options?, rows?, columns?}` — e.g.
  `"[{\"type\":\"text\",\"label\":\"Name\",\"required\":true}]"`
- **rules / rule**: `{match: "all"|"any", if: [{fieldId, operator, value?}], then: [{action, targetId?, formula?, emailTo?}]}`
- **answers**: an object keyed by field id, values string or string-array — e.g.
  `"{\"f_1\":\"hola\",\"f_2\":[\"a\",\"b\"]}"`

Build these with your JSON library (`objectMapper.writeValueAsString(...)`) rather than by hand.

## Errors

Non-2xx responses raise `AnotaApiError` with the HTTP status and the server's message:

```java
try {
    anota.editField(formId, fieldId, field);
} catch (AnotaApiError e) {
    System.err.println(e.getStatus() + ": " + e.getMessage());
}
```

The message comes from the response body's `detail` field (ASP.NET problem details),
falling back to `title`, then the raw body. Network failures are **not** wrapped — they
surface as the native `java.io.IOException` / `InterruptedException` from `HttpClient`.

Note: once a form has been published, its existing fields are locked (`editField`/
`deleteField` return 400); you can always `addFields`.

## License

MIT — see [LICENSE](LICENSE).

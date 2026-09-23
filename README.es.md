# anota-api-java · Cliente oficial de Java para la API de [anota](https://anota.cloud)

**[Read me in English](README.md)** · [Referencia interactiva de la API](https://anota.cloud/developers) · [Todos los SDK](https://github.com/anotacloud/anota-api)

![CI](https://github.com/anotacloud/anota-api-java/actions/workflows/ci.yml/badge.svg)

Crea y publica formularios, edita campos y lógica condicional, lee y escribe
respuestas, y conecta webhooks — todo lo que la API REST de anota puede hacer, desde Java.

Este es un **cliente ligero**: no tiene dependencias en tiempo de ejecución (solo
`java.net.http` del JDK) y cada método devuelve la respuesta del servidor como una
cadena JSON (`String`) en crudo. Como la biblioteca estándar de Java no incluye un
analizador de JSON, lo combinas con la biblioteca JSON que ya usas — Jackson, Gson u
otra — para leer las respuestas y para construir los argumentos `fields`, `rules` y
`answers`, que se pasan como **cadenas JSON preserializadas**.

Requiere Java 11 o superior.

## Instalación

**JitPack** (Maven). Agrega el repositorio JitPack y la dependencia:

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

O descarga el código fuente directamente:
[ZIP](https://github.com/anotacloud/anota-api-java/archive/refs/heads/main.zip) ·
[Tarball](https://github.com/anotacloud/anota-api-java/archive/refs/heads/main.tar.gz).

## Inicio rápido

```java
import cloud.anota.AnotaClient;

AnotaClient anota = new AnotaClient(System.getenv("ANOTA_API_KEY"));

// los campos (fields) son un arreglo JSON preserializado
String form = anota.createForm(
        "Contáctanos",
        "[{\"type\":\"text\",\"label\":\"Nombre\",\"required\":true}]");

// después de leer el id del nuevo formulario desde `form` (con tu biblioteca JSON):
anota.publishForm(formId);
anota.createSubmission(formId, "{\"nombre\":\"Ada\"}");   // las respuestas son una cadena de objeto JSON
String submissions = anota.listSubmissions(formId);        // cadena JSON en crudo
```

Hay un script completo y ejecutable en [`examples/EndToEnd.java`](examples/EndToEnd.java).

## Autenticación

Crea una clave de API en tu workspace en https://anota.cloud/api-keys y pásala al
cliente. Las claves se ven como `anota_sk_…` y también habilitan el conector MCP de Claude.

```java
AnotaClient anota = new AnotaClient("anota_sk_…");
// URL base personalizada (autoalojamiento / pruebas):
AnotaClient staging = new AnotaClient("anota_sk_…", "https://staging.anota.cloud/api/v1");
```

## Todos los métodos

Cada método devuelve el cuerpo de la respuesta JSON en crudo como `String` (o `null` si
el cuerpo de una respuesta 2xx está vacío) y puede lanzar `AnotaApiError`,
`java.io.IOException` o `InterruptedException`.

| # | Método | HTTP |
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

**El secreto de firma del webhook se muestra una sola vez.** `addWebhook(formId, url)` devuelve el `secret` completo (`whsec_…`) en su respuesta (`id`, `formId`, `url`, `secret`, `note`): guárdalo en ese momento. `listWebhooks(formId)` nunca lo devuelve: cada fila trae `secretHint` (`whsec_…` más los últimos 4 caracteres, o solo `whsec_…` si el secreto es corto) y `secretNote` en lugar de `secret`. Si lo pierdes, elimina el webhook y vuelve a agregarlo para obtener un secreto nuevo. Consulta [CHANGELOG.md](CHANGELOG.md).

`listSubmissions` y `listTemplates` también tienen sobrecargas de conveniencia
(`listSubmissions(formId)` usa por defecto página 1, tamaño de página 25, sin filtro de
estado; `listTemplates()` usa por defecto `es`). `createForm(title, fields)` omite la descripción.

### Argumentos como cadenas JSON

`fields`, `field`, `rules`, `rule` y `answers` se aceptan como cadenas JSON
preserializadas para que el cliente se mantenga sin dependencias:

- **fields / field**: `{type, label, required?, options?, rows?, columns?}` — por ejemplo
  `"[{\"type\":\"text\",\"label\":\"Nombre\",\"required\":true}]"`
- **rules / rule**: `{match: "all"|"any", if: [{fieldId, operator, value?}], then: [{action, targetId?, formula?, emailTo?}]}`
- **answers**: un objeto indexado por id de campo, con valores de tipo cadena o arreglo de
  cadenas — por ejemplo `"{\"f_1\":\"hola\",\"f_2\":[\"a\",\"b\"]}"`

Construye estos valores con tu biblioteca JSON (`objectMapper.writeValueAsString(...)`)
en lugar de hacerlo a mano.

## Errores

Las respuestas que no son 2xx lanzan `AnotaApiError` con el código de estado HTTP y el
mensaje del servidor:

```java
try {
    anota.editField(formId, fieldId, field);
} catch (AnotaApiError e) {
    System.err.println(e.getStatus() + ": " + e.getMessage());
}
```

El mensaje proviene del campo `detail` del cuerpo de la respuesta (problem details de
ASP.NET), recurriendo a `title` y luego al cuerpo en crudo. Los errores de red **no** se
envuelven — se propagan como las excepciones nativas `java.io.IOException` /
`InterruptedException` de `HttpClient`.

Nota: una vez que un formulario ha sido publicado, sus campos existentes quedan
bloqueados (`editField`/`deleteField` devuelven 400); siempre puedes usar `addFields`.

## Licencia

MIT — consulta [LICENSE](LICENSE).

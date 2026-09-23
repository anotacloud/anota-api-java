package cloud.anota;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A thin, dependency-free Java client over the anota REST API.
 *
 * <p>The client is intentionally minimal: every method returns the server's response
 * body as a raw JSON {@code String} (or {@code null} for an empty 2xx body). The Java
 * standard library ships no JSON parser, so pair this client with the JSON library of
 * your choice (Jackson, Gson, …) to read responses and to build the {@code fields},
 * {@code rules} and {@code answers} arguments, which are accepted as pre-serialized
 * JSON strings.
 *
 * <pre>{@code
 * AnotaClient anota = new AnotaClient(System.getenv("ANOTA_API_KEY"));
 * String form = anota.createForm(
 *     "Contact us",
 *     "[{\"type\":\"text\",\"label\":\"Name\",\"required\":true}]");
 * }</pre>
 *
 * <p>Non-2xx responses raise {@link AnotaApiError}. Network failures propagate as the
 * native {@link IOException} / {@link InterruptedException} from
 * {@link java.net.http.HttpClient}.
 */
public class AnotaClient {

    private static final String DEFAULT_BASE_URL = "https://anota.cloud/api/v1";

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;

    /** Creates a client against the production base URL {@code https://anota.cloud/api/v1}. */
    public AnotaClient(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL);
    }

    /** Creates a client against a custom base URL (for testing or self-hosting). */
    public AnotaClient(String apiKey, String baseUrl) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException(
                "apiKey is required (create one at https://anota.cloud/api-keys)");
        }
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.httpClient = HttpClient.newHttpClient();
    }

    // ----- core -----

    /**
     * Sends one HTTP request and returns the response body as a raw JSON string
     * (or {@code null} when the body is empty).
     *
     * @param jsonBody a pre-serialized JSON body, or {@code null} for no body
     * @param query    query parameters; entries with a {@code null} value are omitted
     * @throws AnotaApiError        on any non-2xx response
     * @throws IOException          on a network/transport failure (native, unwrapped)
     * @throws InterruptedException if the send is interrupted (native, unwrapped)
     */
    private String request(String method, String path, String jsonBody, Map<String, String> query)
            throws IOException, InterruptedException {
        StringBuilder url = new StringBuilder(baseUrl).append(path);
        if (query != null) {
            boolean first = true;
            for (Map.Entry<String, String> e : query.entrySet()) {
                if (e.getValue() == null) continue;
                url.append(first ? '?' : '&');
                url.append(encode(e.getKey())).append('=').append(encode(e.getValue()));
                first = false;
            }
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .header("Authorization", "Bearer " + apiKey);
        HttpRequest.BodyPublisher publisher;
        if (jsonBody == null) {
            publisher = HttpRequest.BodyPublishers.noBody();
        } else {
            publisher = HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8);
            builder.header("Content-Type", "application/json");
        }
        HttpRequest httpRequest = builder.method(method, publisher).build();

        HttpResponse<String> response =
                httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        String body = response.body();
        if (status < 200 || status >= 300) {
            throw new AnotaApiError(status, extractMessage(body));
        }
        return (body == null || body.isEmpty()) ? null : body;
    }

    // ----- forms -----

    public String listForms() throws IOException, InterruptedException {
        return request("GET", "/forms", null, null);
    }

    public String createForm(String title, String fields) throws IOException, InterruptedException {
        return createForm(title, fields, null);
    }

    public String createForm(String title, String fields, String description)
            throws IOException, InterruptedException {
        StringBuilder body = new StringBuilder("{");
        body.append("\"title\":").append(str(title));
        body.append(",\"fields\":").append(fields);
        if (description != null) {
            body.append(",\"description\":").append(str(description));
        }
        body.append("}");
        return request("POST", "/forms", body.toString(), null);
    }

    public String getForm(String formId) throws IOException, InterruptedException {
        return request("GET", "/forms/" + formId, null, null);
    }

    public String addFields(String formId, String fields) throws IOException, InterruptedException {
        return request("POST", "/forms/" + formId + "/fields", "{\"fields\":" + fields + "}", null);
    }

    public String editField(String formId, String fieldId, String field)
            throws IOException, InterruptedException {
        return request("PATCH", "/forms/" + formId + "/fields/" + fieldId,
                "{\"field\":" + field + "}", null);
    }

    public String deleteField(String formId, String fieldId) throws IOException, InterruptedException {
        return request("DELETE", "/forms/" + formId + "/fields/" + fieldId, null, null);
    }

    public String publishForm(String formId) throws IOException, InterruptedException {
        return request("POST", "/forms/" + formId + "/publish", null, null);
    }

    public String renameForm(String formId, String title) throws IOException, InterruptedException {
        return request("PATCH", "/forms/" + formId, "{\"title\":" + str(title) + "}", null);
    }

    public String setPdfTemplate(String formId, String key) throws IOException, InterruptedException {
        return request("PUT", "/forms/" + formId + "/pdf-template", "{\"key\":" + str(key) + "}", null);
    }

    public String deleteForm(String formId) throws IOException, InterruptedException {
        return request("DELETE", "/forms/" + formId, null, null);
    }

    public String cloneForm(String formId) throws IOException, InterruptedException {
        return request("POST", "/forms/" + formId + "/clone", null, null);
    }

    // ----- logic rules -----

    public String addLogicRules(String formId, String rules) throws IOException, InterruptedException {
        return request("POST", "/forms/" + formId + "/logic-rules", "{\"rules\":" + rules + "}", null);
    }

    public String editLogicRule(String formId, String ruleId, String rule)
            throws IOException, InterruptedException {
        return request("PUT", "/forms/" + formId + "/logic-rules/" + ruleId,
                "{\"rule\":" + rule + "}", null);
    }

    public String deleteLogicRule(String formId, String ruleId)
            throws IOException, InterruptedException {
        return request("DELETE", "/forms/" + formId + "/logic-rules/" + ruleId, null, null);
    }

    // ----- submissions -----

    public String listSubmissions(String formId) throws IOException, InterruptedException {
        return listSubmissions(formId, 1, 25, null);
    }

    public String listSubmissions(String formId, int page, int pageSize, String status)
            throws IOException, InterruptedException {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("page", String.valueOf(page));
        query.put("pageSize", String.valueOf(pageSize));
        query.put("status", status);
        return request("GET", "/forms/" + formId + "/submissions", null, query);
    }

    public String getSubmission(String submissionId) throws IOException, InterruptedException {
        return request("GET", "/submissions/" + submissionId, null, null);
    }

    public String createSubmission(String formId, String answers)
            throws IOException, InterruptedException {
        return request("POST", "/forms/" + formId + "/submissions",
                "{\"answers\":" + answers + "}", null);
    }

    public String setSubmissionStatus(String submissionId, String status)
            throws IOException, InterruptedException {
        return request("PATCH", "/submissions/" + submissionId + "/status",
                "{\"status\":" + str(status) + "}", null);
    }

    public String deleteSubmission(String submissionId) throws IOException, InterruptedException {
        return request("DELETE", "/submissions/" + submissionId, null, null);
    }

    public String submissionStats(String formId) throws IOException, InterruptedException {
        return request("GET", "/forms/" + formId + "/stats", null, null);
    }

    // ----- templates -----

    public String listTemplates() throws IOException, InterruptedException {
        return listTemplates("es");
    }

    public String listTemplates(String language) throws IOException, InterruptedException {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("language", language);
        return request("GET", "/templates", null, query);
    }

    public String createFormFromTemplate(String templateId) throws IOException, InterruptedException {
        return request("POST", "/forms/from-template/" + templateId, null, null);
    }

    // ----- webhooks -----

    /**
     * Lists a form's webhooks. Each row has id, url, events, enabled, secretHint and secretNote.
     * The full signing secret is never returned here: secretHint is a masked form
     * ("whsec_…" + last 4 characters, or just "whsec_…" for short secrets) that identifies
     * which secret a receiver holds, and secretNote explains the show-once rule. To replace a
     * lost secret, delete the webhook and add it again.
     */
    public String listWebhooks(String formId) throws IOException, InterruptedException {
        return request("GET", "/forms/" + formId + "/webhooks", null, null);
    }

    /**
     * Registers a webhook URL that receives submission.created events. The response
     * (id, formId, url, secret, note) is the ONLY place the full signing secret appears:
     * store it now, it cannot be read back later (listWebhooks shows only secretHint).
     */
    public String addWebhook(String formId, String url) throws IOException, InterruptedException {
        return request("POST", "/forms/" + formId + "/webhooks", "{\"url\":" + str(url) + "}", null);
    }

    public String deleteWebhook(String formId, String webhookId)
            throws IOException, InterruptedException {
        return request("DELETE", "/forms/" + formId + "/webhooks/" + webhookId, null, null);
    }

    // ----- JSON helpers (no third-party dependency) -----

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Serializes a string as a JSON string literal, escaping per RFC 8259. */
    static String str(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }

    /**
     * Extracts the error message from a problem-details body without a JSON parser:
     * the {@code detail} string field, else {@code title}, else the raw body.
     */
    static String extractMessage(String body) {
        if (body == null || body.isEmpty()) return "";
        String detail = jsonStringField(body, "detail");
        if (detail != null) return detail;
        String title = jsonStringField(body, "title");
        if (title != null) return title;
        return body;
    }

    /** Reads the value of a top-level string property {@code "key": "..."} from flat JSON. */
    static String jsonStringField(String json, String key) {
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        i += needle.length();
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length() || json.charAt(i) != ':') return null;
        i++;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length() || json.charAt(i) != '"') return null; // value is not a string
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i++);
            if (c == '\\') {
                if (i >= json.length()) break;
                char n = json.charAt(i++);
                switch (n) {
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case '/':  sb.append('/');  break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    case 'b':  sb.append('\b'); break;
                    case 'f':  sb.append('\f'); break;
                    case 'u':
                        if (i + 4 <= json.length()) {
                            sb.append((char) Integer.parseInt(json.substring(i, i + 4), 16));
                            i += 4;
                        }
                        break;
                    default: sb.append(n);
                }
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        return null;
    }
}

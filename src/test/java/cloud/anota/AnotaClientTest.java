package cloud.anota;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Behavioral tests for {@link AnotaClient}, driven against a real in-process
 * {@link HttpServer} (JDK built-in). Each test records the request the client
 * actually sent and asserts on it.
 */
class AnotaClientTest {

    private HttpServer server;
    private AnotaClient client;

    // captured request state
    private String capturedMethod;
    private String capturedPath;   // path only
    private String capturedQuery;  // raw query string (may be null)
    private String capturedAuth;
    private String capturedBody;

    // canned response for the next request
    private int responseStatus = 200;
    private String responseBody = "{}";
    private String responseContentType = "application/json";

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        int port = server.getAddress().getPort();
        client = new AnotaClient("anota_sk_test", "http://127.0.0.1:" + port);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        capturedMethod = exchange.getRequestMethod();
        capturedPath = exchange.getRequestURI().getPath();
        capturedQuery = exchange.getRequestURI().getRawQuery();
        capturedAuth = exchange.getRequestHeaders().getFirst("Authorization");
        try (InputStream in = exchange.getRequestBody()) {
            capturedBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", responseContentType);
        exchange.sendResponseHeaders(responseStatus, out.length == 0 ? -1 : out.length);
        exchange.getResponseBody().write(out);
        exchange.close();
    }

    // (a) listForms sends GET {base}/forms with the Bearer header
    @Test
    void listFormsSendsGetWithBearerHeader() throws Exception {
        responseBody = "[]";
        String result = client.listForms();

        assertEquals("GET", capturedMethod);
        assertEquals("/forms", capturedPath);
        assertEquals("Bearer anota_sk_test", capturedAuth);
        assertEquals("[]", result);
    }

    // (b) createSubmission sends the JSON body {"answers":{"f_1":"hola"}}
    @Test
    void createSubmissionSendsAnswersBody() throws Exception {
        client.createSubmission("form_1", "{\"f_1\":\"hola\"}");

        assertEquals("POST", capturedMethod);
        assertEquals("/forms/form_1/submissions", capturedPath);
        assertEquals("{\"answers\":{\"f_1\":\"hola\"}}", capturedBody);
    }

    // (c) a 400 problem-details response rejects with AnotaApiError (status 400, message from detail)
    @Test
    void nonSuccessRaisesAnotaApiError() {
        responseStatus = 400;
        responseBody = "{\"detail\":\"Error: bad\"}";

        AnotaApiError error = assertThrows(AnotaApiError.class, () -> client.listForms());
        assertEquals(400, error.getStatus());
        assertEquals("Error: bad", error.getMessage());
    }

    // (d) listSubmissions(id, 2, 10, "New") builds ?page=2&pageSize=10&status=New
    @Test
    void listSubmissionsBuildsQueryString() throws Exception {
        client.listSubmissions("form_1", 2, 10, "New");

        assertEquals("GET", capturedMethod);
        assertEquals("/forms/form_1/submissions", capturedPath);
        assertEquals("page=2&pageSize=10&status=New", capturedQuery);
    }

    // bonus: a null status is omitted from the query string
    @Test
    void listSubmissionsOmitsNullStatus() throws Exception {
        client.listSubmissions("form_1");

        assertEquals("page=1&pageSize=25", capturedQuery);
    }

    // bonus: an empty 2xx body maps to null
    @Test
    void emptyBodyReturnsNull() throws Exception {
        responseStatus = 204;
        responseBody = "";
        assertNull(client.deleteForm("form_1"));
    }
}

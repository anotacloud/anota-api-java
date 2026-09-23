import cloud.anota.AnotaClient;

/**
 * End-to-end example: create a form, add a field, publish it, create a submission,
 * then list submissions. Reads the API key from the ANOTA_API_KEY environment variable.
 *
 * <p>This client is a thin wrapper that returns raw JSON strings, so responses are
 * printed as-is. In a real application, parse them with Jackson, Gson, or another
 * JSON library. The {@code fields} and {@code answers} arguments are passed as
 * pre-serialized JSON strings.
 *
 * <p>Compile and run with the built jar on the classpath, e.g.:
 * <pre>
 *   mvn -q package
 *   javac -cp target/anota-api-2.0.0.jar examples/EndToEnd.java -d target/examples
 *   java  -cp "target/anota-api-2.0.0.jar;target/examples" EndToEnd   # ';' -&gt; ':' on macOS/Linux
 * </pre>
 */
public class EndToEnd {

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("ANOTA_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Set ANOTA_API_KEY to your anota API key (https://anota.cloud/api-keys)");
            System.exit(1);
        }

        AnotaClient anota = new AnotaClient(apiKey);

        System.out.println("Creating form...");
        String form = anota.createForm(
                "SDK smoke test",
                "[{\"type\":\"text\",\"label\":\"Name\",\"required\":true}]",
                "Created by the anota-api-java example");
        System.out.println(form);

        // Extract the new form's id from the response. Use a real JSON parser in your app;
        // here we reuse the client's tiny built-in string-field reader for brevity.
        String formId = readId(form);
        System.out.println("Form id: " + formId);

        System.out.println("Adding a field...");
        System.out.println(anota.addFields(formId,
                "[{\"type\":\"email\",\"label\":\"Email\",\"required\":true}]"));

        System.out.println("Publishing form...");
        System.out.println(anota.publishForm(formId));

        System.out.println("Creating a submission...");
        System.out.println(anota.createSubmission(formId,
                "{\"name\":\"Ada Lovelace\",\"email\":\"ada@example.com\"}"));

        System.out.println("Listing submissions...");
        System.out.println(anota.listSubmissions(formId));
    }

    /** Minimal id reader for the example only — parse with a real JSON library in production. */
    private static String readId(String json) {
        int i = json.indexOf("\"id\"");
        if (i < 0) return "";
        i = json.indexOf('"', json.indexOf(':', i) + 1) + 1;
        return json.substring(i, json.indexOf('"', i));
    }
}

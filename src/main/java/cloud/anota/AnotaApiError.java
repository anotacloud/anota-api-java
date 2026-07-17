package cloud.anota;

/**
 * Thrown when the anota API returns a non-2xx response.
 *
 * <p>Carries the HTTP status code and the server's error message. The message is
 * taken from the {@code detail} field of an RFC 7807 problem-details body, falling
 * back to {@code title}, then to the raw response body when neither is present.
 *
 * <p>This is an unchecked exception. Network-level failures are <em>not</em> wrapped
 * in this type: they surface as the native {@link java.io.IOException} /
 * {@link InterruptedException} thrown by {@link java.net.http.HttpClient}.
 */
public class AnotaApiError extends RuntimeException {

    private final int status;

    public AnotaApiError(int status, String message) {
        super(message);
        this.status = status;
    }

    /** The HTTP status code of the failed response. */
    public int getStatus() {
        return status;
    }
}

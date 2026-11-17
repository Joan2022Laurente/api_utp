package com.utp.horario.support;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Cliente HTTP falso para pruebas de adaptadores externos.
 *
 * <p>Permite responder con un status y un cuerpo fijos, o delegar en un manejador que
 * inspeccione la URI solicitada. Registra todas las peticiones enviadas para poder
 * verificar que el adaptador no sale a la red cuando debe abortar.</p>
 */
public final class FakeHttpClient extends HttpClient {

    /** Respuesta HTTP simulada. */
    public record Response(int status, String body) {
    }

    /** Manejador que produce una respuesta para cada petición. */
    @FunctionalInterface
    public interface Handler {
        Response handle(HttpRequest request) throws IOException, InterruptedException;
    }

    private final Handler handler;
    private final List<HttpRequest> requests = new ArrayList<>();

    public FakeHttpClient(Handler handler) {
        this.handler = handler;
    }

    /** Responde siempre el mismo status y cuerpo. */
    public static FakeHttpClient always(int status, String body) {
        return new FakeHttpClient(request -> new Response(status, body));
    }

    /** Peticiones recibidas, en orden. */
    public List<HttpRequest> requests() {
        return List.copyOf(requests);
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
            throws IOException, InterruptedException {
        synchronized (requests) {
            requests.add(request);
        }
        Response response = handler.handle(request);
        @SuppressWarnings("unchecked")
        T body = (T) response.body();
        return new FakeResponse<>(request, response.status(), body);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
        throw new UnsupportedOperationException("sendAsync no se usa en las pruebas");
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request,
            HttpResponse.BodyHandler<T> responseBodyHandler,
            HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        throw new UnsupportedOperationException("sendAsync no se usa en las pruebas");
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
        return Optional.empty();
    }

    @Override
    public Optional<Duration> connectTimeout() {
        return Optional.of(Duration.ofSeconds(5));
    }

    @Override
    public Redirect followRedirects() {
        return Redirect.NEVER;
    }

    @Override
    public Optional<ProxySelector> proxy() {
        return Optional.empty();
    }

    @Override
    public SSLContext sslContext() {
        return null;
    }

    @Override
    public SSLParameters sslParameters() {
        return null;
    }

    @Override
    public Optional<Authenticator> authenticator() {
        return Optional.empty();
    }

    @Override
    public Version version() {
        return Version.HTTP_1_1;
    }

    @Override
    public Optional<Executor> executor() {
        return Optional.empty();
    }

    private static final class FakeResponse<T> implements HttpResponse<T> {

        private final HttpRequest request;
        private final int statusCode;
        private final T body;

        private FakeResponse(HttpRequest request, int statusCode, T body) {
            this.request = request;
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (name, value) -> true);
        }

        @Override
        public T body() {
            return body;
        }

        @Override
        public Optional<javax.net.ssl.SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }
    }
}

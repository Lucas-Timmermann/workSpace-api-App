package com.example.api_tp.client.arca;

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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

public class StubHttpClient extends HttpClient {
    @FunctionalInterface
    public interface Handler {
        HttpResponse<String> handle(HttpRequest request) throws IOException, InterruptedException;
    }

    private final Handler handler;
    private final AtomicInteger calls = new AtomicInteger();

    public StubHttpClient(Handler handler) {
        this.handler = handler;
    }

    public int calls() {
        return calls.get();
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
            throws IOException, InterruptedException {
        calls.incrementAndGet();
        @SuppressWarnings("unchecked")
        HttpResponse<T> response = (HttpResponse<T>) handler.handle(request);
        return response;
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        throw new UnsupportedOperationException();
    }

    @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
    @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
    @Override public Redirect followRedirects() { return Redirect.NEVER; }
    @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
    @Override public SSLContext sslContext() { return null; }
    @Override public SSLParameters sslParameters() { return null; }
    @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
    @Override public Version version() { return Version.HTTP_1_1; }
    @Override public Optional<Executor> executor() { return Optional.empty(); }

    public static HttpResponse<String> response(int status, String body) {
        return new SimpleStringResponse(status, body == null ? "" : body);
    }

    static class SimpleStringResponse implements HttpResponse<String> {
        private final int status;
        private final String body;
        SimpleStringResponse(int status, String body) { this.status = status; this.body = body; }
        @Override public int statusCode() { return status; }
        @Override public HttpRequest request() { return null; }
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a,b)->true); }
        @Override public String body() { return body; }
        @Override public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return null; }
        @Override public Version version() { return Version.HTTP_1_1; }
    }
}

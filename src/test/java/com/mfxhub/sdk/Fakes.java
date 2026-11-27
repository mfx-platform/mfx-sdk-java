package com.mfxhub.sdk;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import java.util.function.Supplier;
import javax.net.ssl.SSLSession;
import org.jspecify.annotations.Nullable;

/** What the tests stand in for the network with, and the test key. */
final class Fakes {
    /** The API description's test key: the 32 bytes 00 to 1f. Public on purpose. */
    static final byte[] SEED = new byte[32];

    static {
        for (int i = 0; i < SEED.length; i++) {
            SEED[i] = (byte) i;
        }
    }

    /** The test key's public half, as the platform holds it. */
    static final String PUBLIC_KEY = "MCowBQYDK2VwAyEAA6EHv/POEL4dcN0Y50vAmWfk1jCbpQ1fHdyGZBJVMbg=";

    static final String PEM = """
            -----BEGIN PRIVATE KEY-----
            MC4CAQAwBQYDK2VwBCIEIAABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGhscHR4f
            -----END PRIVATE KEY-----
            """;

    static final URI BASE = URI.create("https://api.example.test/v1");

    private Fakes() {
    }

    static SigningKey key() {
        return SigningKey.fromSeed(SEED);
    }

    static boolean verifies(byte[] message, String signature) throws Exception {
        PublicKey key = KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY)));
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(key);
        verifier.update(message);
        return verifier.verify(Base64.getDecoder().decode(signature));
    }

    /** One answer a fake transport hands out. */
    @FunctionalInterface
    interface Answer extends Supplier<CompletableFuture<HttpResponse<byte[]>>> {
    }

    /** What one request carried, as the tests read it. */
    @SuppressWarnings("ArrayRecordComponent") // a test's own view of what was sent, never compared as a record
    record Sent(String method, URI url, Map<String, String> headers, byte[] body) {
        String header(String name) {
            String value = headers.get(name.toLowerCase(java.util.Locale.ROOT));
            if (value == null) {
                throw new AssertionError("no " + name + " header in " + headers.keySet());
            }
            return value;
        }

        boolean has(String name) {
            return headers.containsKey(name.toLowerCase(java.util.Locale.ROOT));
        }

        String pathWithQuery() {
            return url.getRawQuery() == null ? url.getRawPath() : url.getRawPath() + "?" + url.getRawQuery();
        }
    }

    /** A transport that records each request and hands out the answers it was given, in order. */
    static final class Recording implements Transport {
        private final Deque<Answer> answers = new ArrayDeque<>();
        final List<Sent> requests = new ArrayList<>();

        Recording(Answer... answers) {
            this.answers.addAll(List.of(answers));
        }

        @Override
        public CompletableFuture<HttpResponse<byte[]>> send(HttpRequest request) {
            Map<String, String> headers = new TreeMap<>();
            request.headers().map().forEach((name, values) ->
                    headers.put(name.toLowerCase(java.util.Locale.ROOT), String.join(",", values)));
            requests.add(new Sent(request.method(), request.uri(), headers, bodyOf(request)));
            Answer next = answers.poll();
            if (next == null) {
                throw new AssertionError("an unexpected request: " + request.method() + " " + request.uri());
            }
            return next.get();
        }
    }

    static Answer answer(int status, String body, String... headers) {
        return () -> CompletableFuture.completedFuture(response(status, body, headers));
    }

    static Answer failing(Throwable failure) {
        return () -> CompletableFuture.failedFuture(failure);
    }

    static Answer hanging() {
        return CompletableFuture::new;
    }

    static Answer rateLimited(@Nullable String retryAfter) {
        String body = "{\"error\":{\"code\":\"rate_limited\",\"message\":\"slow down\"}}";
        return retryAfter == null ? answer(429, body) : answer(429, body, "Retry-After", retryAfter);
    }

    static HttpResponse<byte[]> response(int status, String body, String... headers) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("Content-Type", List.of("application/json"));
        for (int i = 0; i + 1 < headers.length; i += 2) {
            map.put(headers[i], List.of(headers[i + 1]));
        }
        HttpHeaders read = HttpHeaders.of(map, (name, value) -> true);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return status;
            }

            @Override
            public HttpRequest request() {
                return HttpRequest.newBuilder(BASE).build();
            }

            @Override
            public Optional<HttpResponse<byte[]>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return read;
            }

            @Override
            public byte[] body() {
                return bytes.clone();
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return BASE;
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }

    /** The bytes a request's body publisher sends. */
    static byte[] bodyOf(HttpRequest request) {
        Optional<HttpRequest.BodyPublisher> publisher = request.bodyPublisher();
        if (publisher.isEmpty()) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CompletableFuture<Void> done = new CompletableFuture<>();
        publisher.get().subscribe(new Flow.Subscriber<ByteBuffer>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                out.writeBytes(chunk);
            }

            @Override
            public void onError(Throwable throwable) {
                done.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                done.complete(null);
            }
        });
        done.join();
        return out.toByteArray();
    }

    /** What a future failed with, unwrapped from join()'s CompletionException. */
    static Throwable failure(CompletableFuture<?> future) {
        try {
            future.join();
        } catch (CompletionException failed) {
            return failed.getCause();
        }
        throw new AssertionError("completed, where a failure was expected");
    }
}

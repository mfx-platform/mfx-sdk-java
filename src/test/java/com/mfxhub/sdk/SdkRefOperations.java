package com.mfxhub.sdk;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Which contract operation each public method of the resource clients calls, found by calling it: every
 * method is called once with made-up arguments on a client whose transport records the first request and
 * answers {@code {}}, and that request's method and path are matched against the contract's paths. A method
 * that sends nothing (a builder, a local check) or cannot be called with made-up arguments is not found.
 */
final class SdkRefOperations {
    static final UUID ID = UUID.fromString("0191f0c2-aaaa-7bbb-8ccc-000000000001");

    /** The resource clients a reader reaches from {@link MfxClient}, by their accessor. */
    static final List<String> RESOURCES = List.of("terminals", "execution", "accounts", "tradingAccounts", "md", "risk", "webhooks",
            "expertAdvisors", "copy", "billing", "catalog", "farms", "usage", "maintenance", "tenant");

    /** One contract path: its method, the path's pattern, and how many of its segments are literal. */
    record Route(String method, Pattern path, int literals, String operationId) {
    }

    private SdkRefOperations() {
    }

    /** Every route of {@code spec/openapi.yaml}, read line by line (the file is the generator's own YAML). */
    static List<Route> routes(Path openapi) throws Exception {
        List<Route> out = new ArrayList<>();
        boolean inPaths = false;
        String path = null;
        String method = null;
        for (String line : Files.readAllLines(openapi)) {
            if (line.equals("paths:")) {
                inPaths = true;
                continue;
            }
            if (!inPaths) {
                continue;
            }
            if (!line.isEmpty() && !line.startsWith(" ")) {
                break;
            }
            if (line.startsWith("  /") && line.endsWith(":") && !line.startsWith("   ")) {
                path = line.substring(2, line.length() - 1);
            } else if (line.matches("    (get|post|put|patch|delete):")) {
                method = line.strip().replace(":", "").toUpperCase(java.util.Locale.ROOT);
            } else if (line.startsWith("      operationId: ") && path != null && method != null) {
                String regex = Arrays.stream(path.substring(1).split("/", -1))
                        .map(s -> s.startsWith("{") ? "[^/]+" : Pattern.quote(s)).reduce((a, b) -> a + "/" + b).orElse("");
                int literals = (int) Arrays.stream(path.split("/", -1)).filter(s -> !s.isEmpty() && !s.startsWith("{")).count();
                out.add(new Route(method, Pattern.compile("/" + regex), literals, line.substring("      operationId: ".length()).strip()));
            }
        }
        return out;
    }

    static @Nullable String operationOf(List<Route> routes, String method, String path) {
        return routes.stream().filter(r -> r.method().equals(method) && r.path().matcher(path).matches())
                .max(Comparator.comparingInt(Route::literals)).map(Route::operationId).orElse(null);
    }

    /** {@code Type.method} to the operation its first request names, for every method that sends one. */
    static Map<String, String> discover(List<Route> routes) throws Exception {
        Map<String, String> found = new TreeMap<>();
        Map<String, String> conflicts = new TreeMap<>();
        for (String accessor : RESOURCES) {
            Class<?> type = MfxClient.class.getMethod(accessor).getReturnType();
            for (Method method : type.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers()) || method.isSynthetic()) {
                    continue;
                }
                List<String> sent = new ArrayList<>();
                Transport transport = request -> {
                    sent.add(request.method() + " " + request.uri().getRawPath());
                    return CompletableFuture.completedFuture(Fakes.response(200, "{}"));
                };
                try (MfxClient client = MfxClient.builder(Fakes.BASE, "mfxk_1", Fakes.key()).transport(transport)
                        .clock(Clock.fixed(Instant.parse("2026-11-17T09:00:00Z"), ZoneOffset.UTC))
                        .pause(wait -> CompletableFuture.completedFuture(null)).build()) {
                    Object resource = MfxClient.class.getMethod(accessor).invoke(client);
                    call(resource, method);
                }
                if (sent.isEmpty()) {
                    continue;
                }
                String first = sent.get(0);
                int space = first.indexOf(' ');
                String path = first.substring(space + 1).replaceFirst("^/v1", "");
                String operation = operationOf(routes, first.substring(0, space), path);
                String key = type.getSimpleName() + "." + method.getName();
                if (operation == null) {
                    conflicts.put(key, "no contract route for " + first);
                } else if (found.containsKey(key) && !found.get(key).equals(operation)) {
                    conflicts.put(key, found.get(key) + " and " + operation);
                } else {
                    found.put(key, operation);
                }
            }
        }
        if (!conflicts.isEmpty()) {
            throw new AssertionError("methods whose requests name no single operation: " + conflicts);
        }
        return found;
    }

    private static void call(Object resource, Method method) {
        Object[] arguments = new Object[method.getParameterCount()];
        Class<?>[] parameters = method.getParameterTypes();
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = sample(parameters[i]);
        }
        try {
            Object result = method.invoke(resource, arguments);
            if (result instanceof CompletableFuture<?> future) {
                future.orTimeout(2, TimeUnit.SECONDS).handle((value, failure) -> null).join();
            } else if (result instanceof Iterable<?> iterable) {
                Iterator<?> iterator = iterable.iterator();
                if (iterator.hasNext()) {
                    iterator.next();
                }
            }
        } catch (InvocationTargetException | IllegalAccessException | RuntimeException refused) {
            // Refused before sending, or failed reading "{}": what was sent is what counts.
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static @Nullable Object sample(Class<?> type) {
        if (type == UUID.class) {
            return ID;
        }
        if (type == String.class) {
            return "k1";
        }
        if (type == long.class || type == Long.class) {
            return 1L;
        }
        if (type == int.class || type == Integer.class) {
            return 1;
        }
        if (type == boolean.class || type == Boolean.class) {
            return false;
        }
        if (type == double.class || type == Double.class) {
            return 1.0;
        }
        if (type == BigDecimal.class) {
            return BigDecimal.ONE;
        }
        if (type == Duration.class) {
            return Duration.ofSeconds(1);
        }
        if (type == Instant.class) {
            return Instant.parse("2026-11-17T08:00:00Z");
        }
        if (type == OffsetDateTime.class) {
            return OffsetDateTime.parse("2026-11-17T08:00:00Z");
        }
        if (type == LocalDate.class) {
            return LocalDate.parse("2026-11-17");
        }
        if (type == URI.class) {
            return URI.create("https://example.test/hook");
        }
        if (type == List.class || type == Iterable.class || type == java.util.Collection.class) {
            return List.of();
        }
        if (type == Set.class) {
            return Set.of();
        }
        if (type == Map.class) {
            return new LinkedHashMap<>();
        }
        if (type == byte[].class) {
            return new byte[0];
        }
        if (type.isArray()) {
            return Array.newInstance(type.getComponentType(), 0);
        }
        if (type.isEnum()) {
            return type.getEnumConstants()[0];
        }
        if (type == HttpRequest.class || type == HttpResponse.class) {
            return null;
        }
        for (String factory : List.of("builder", "of", "defaults", "market", "none")) {
            try {
                Method make = type.getMethod(factory);
                if (Modifier.isStatic(make.getModifiers())) {
                    Object made = make.invoke(null);
                    if (type.isInstance(made)) {
                        return made;
                    }
                    Method build = made.getClass().getMethod("build");
                    return build.invoke(made);
                }
            } catch (ReflectiveOperationException | RuntimeException absent) {
                // the next way
            }
        }
        try {
            return type.getConstructor().newInstance();
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return null;
        }
    }
}

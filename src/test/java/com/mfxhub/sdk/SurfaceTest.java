package com.mfxhub.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The surface check: every entry of the shared surface
 * (spec/surface.json), Core and Enriched, is in this SDK's public API, found through the idiom map,
 * unless the map holds it as pending with the pull request that brings it. A whole resource, an error
 * ({@code errors.<Name>}) or an event ({@code events.<Name>}) may be pending as one.
 */
class SurfaceTest {

    private static JsonNode load(String path) throws Exception {
        return MfxJson.MAPPER.readTree(Files.readString(Path.of(path)));
    }

    private static String camel(String snake) {
        StringBuilder out = new StringBuilder();
        boolean upper = false;
        for (int i = 0; i < snake.length(); i++) {
            char ch = snake.charAt(i);
            if (ch == '_') {
                upper = out.length() > 0;
            } else {
                out.append(upper ? Character.toUpperCase(ch) : ch);
                upper = false;
            }
        }
        return out.toString();
    }

    private static @Nullable String mapped(JsonNode map, String section, String key) {
        JsonNode value = map.path(section).get(key);
        return value == null ? null : value.asText();
    }

    private static @Nullable Class<?> type(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException absent) {
            return null;
        }
    }

    private static List<Method> publicMethods(Class<?> type, String name) {
        return Arrays.stream(type.getMethods()).filter(m -> m.getName().equals(name) && Modifier.isPublic(m.getModifiers())).toList();
    }

    private static String name(JsonNode entry) {
        return entry.has("method") ? entry.get("method").asText()
                : entry.has("property") ? entry.get("property").asText() : entry.get("protocol").asText();
    }

    /** One problem for each entry this SDK's public surface does not have and the map does not hold as pending. */
    static List<String> missing(JsonNode reference, JsonNode map) {
        List<String> problems = new ArrayList<>();
        JsonNode pending = map.path("pending");
        for (JsonNode entry : reference.path("methods")) {
            String resource = entry.path("resource").asText();
            String name = name(entry);
            String key = resource + "." + name;
            if (pending.has(key) || pending.has(resource)) {
                continue;
            }
            String typeName = mapped(map, "resources", resource);
            Class<?> type = typeName == null ? null : type(typeName);
            if (type == null || !Modifier.isPublic(type.getModifiers())) {
                problems.add(key + ": no public type for '" + resource + "'");
                continue;
            }
            if (entry.has("protocol")) {
                String wanted = mapped(map, "protocols", key);
                Class<?> protocol = wanted == null ? null : type(wanted);
                if (protocol == null || !protocol.isAssignableFrom(type)) {
                    problems.add(key + ": " + type.getSimpleName() + " does not implement " + (wanted == null ? "a mapped interface" : wanted));
                }
                continue;
            }
            if (entry.has("property")) {
                boolean found = publicMethods(type, camel(name)).stream()
                        .anyMatch(m -> m.getParameterCount() == 0 && !Modifier.isStatic(m.getModifiers()));
                if (!found) {
                    problems.add(key + ": no public accessor " + type.getSimpleName() + "." + camel(name) + "()");
                }
                continue;
            }
            String member = mapped(map, "methods", key);
            if (member == null) {
                member = camel(name);
            }
            List<? extends Executable> candidates = publicMethods(type, member);
            if (candidates.isEmpty()) {
                problems.add(key + ": no public " + type.getSimpleName() + "." + member);
                continue;
            }
            for (JsonNode arg : entry.path("args")) {
                String argName = arg.path("name").asText();
                String target = map.path("arguments").path(key).has(argName)
                        ? map.path("arguments").path(key).get(argName).asText() : camel(argName);
                int hash = target.indexOf('#');
                if (hash >= 0) {
                    Class<?> owner = type(target.substring(0, hash));
                    String setting = target.substring(hash + 1);
                    if (owner == null || publicMethods(owner, setting).isEmpty() || !reaches(candidates, owner)) {
                        problems.add(key + "(" + argName + "): no " + target + " reached from " + type.getSimpleName() + "." + member);
                    }
                } else if (candidates.stream().noneMatch(c -> Arrays.stream(c.getParameters()).map(Parameter::getName).anyMatch(target::equals))) {
                    problems.add(key + "(" + argName + "): " + type.getSimpleName() + "." + member + " has no parameter " + target);
                }
            }
            boolean async = entry.path("async").asBoolean(false);
            if (async && !map.path("blocking").has(key)) {
                boolean futures = candidates.stream().allMatch(c -> CompletableFuture.class.isAssignableFrom(((Method) c).getReturnType()));
                if (!futures) {
                    problems.add(key + ": " + type.getSimpleName() + "." + member + " is async in the reference but returns no CompletableFuture");
                }
            }
        }
        Set<String> errors = new HashSet<>();
        reference.path("errors").forEach(e -> errors.add(e.path("error").asText()));
        for (String error : errors) {
            if (pending.has("errors." + error)) {
                continue;
            }
            String java = "com.mfxhub.sdk." + javaError(error);
            Class<?> found = type(java);
            if (found == null || !Modifier.isPublic(found.getModifiers()) || !MfxApiException.class.isAssignableFrom(found)) {
                problems.add("errors." + error + ": no public " + java);
            }
        }
        for (JsonNode event : reference.path("events")) {
            String name = event.path("event").asText();
            if (pending.has("events." + name)) {
                continue;
            }
            String java = map.path("events").has(name) ? map.path("events").get(name).asText() : "com.mfxhub.sdk." + name;
            Class<?> found = type(java);
            if (found == null || !Modifier.isPublic(found.getModifiers())) {
                problems.add("events." + name + ": no public " + java);
                continue;
            }
            for (JsonNode field : event.path("fields")) {
                String accessor = camel(field.asText());
                boolean has = publicMethods(found, accessor).stream()
                        .anyMatch(m -> m.getParameterCount() == 0 && !Modifier.isStatic(m.getModifiers()));
                if (!has) {
                    problems.add("events." + name + "." + field.asText() + ": no public accessor " + found.getSimpleName() + "." + accessor + "()");
                }
            }
        }
        return problems;
    }

    /**
     * The Java name of an error in spec/surface.json: {@code FooError} is {@code FooException}, and a name
     * the surface gives without the suffix ({@code ServiceAgreementNotAccepted}) gains it.
     */
    static String javaError(String python) {
        String stem = python.endsWith("Error") ? python.substring(0, python.length() - "Error".length()) : python;
        return stem + "Exception";
    }

    /** Every name the map may use: each resource, each resource.member, errors.Name and events.Name. */
    static Set<String> referenceKeys(JsonNode reference) {
        Set<String> keys = new HashSet<>();
        reference.path("methods").forEach(e -> {
            keys.add(e.path("resource").asText());
            keys.add(e.path("resource").asText() + "." + name(e));
        });
        reference.path("errors").forEach(e -> keys.add("errors." + e.path("error").asText()));
        reference.path("events").forEach(e -> keys.add("events." + e.path("event").asText()));
        return keys;
    }

    /** A builder is reached from a call that takes it, returns it, or takes the type it builds. */
    private static boolean reaches(List<? extends Executable> candidates, Class<?> owner) {
        Class<?> built = owner.getEnclosingClass();
        for (Executable candidate : candidates) {
            for (Class<?> parameter : candidate.getParameterTypes()) {
                if (parameter == owner || parameter == built) {
                    return true;
                }
            }
            if (candidate instanceof Method method && method.getReturnType() == owner) {
                return true;
            }
        }
        return false;
    }

    /**
     * Given the shared surface.json and this SDK's idiom map / When every entry is looked for in
     * the public API / Then each method, property, protocol, argument, error and event is there under its
     * Java name, a CompletableFuture where the surface is async unless the map says why it blocks; an
     * entry the map holds as pending is named with the pull request that brings it, and the map names
     * only entries the surface has.
     */
    @Test
    void everyEntryOfTheReferenceSurfaceIsInThePublicApi() throws Exception {
        JsonNode reference = load("spec/surface.json");
        JsonNode map = load("tools/surface-check/map.json");

        assertEquals(List.of(), missing(reference, map));

        Set<String> keys = referenceKeys(reference);
        for (String section : List.of("resources", "protocols", "methods", "blocking", "arguments", "events", "pending")) {
            map.path(section).fieldNames().forEachRemaining(name -> assertTrue(keys.contains(name), section + " names " + name + ", which the reference has not"));
        }
        map.path("pending").fields().forEachRemaining(e -> assertFalse(e.getValue().asText().isBlank(), e.getKey() + " is pending with no reason"));
    }

    /** The check is not vacuous: a map that points a resource nowhere is caught. */
    @Test
    void aSurfaceTheSdkLacksIsCaught() throws Exception {
        JsonNode reference = load("spec/surface.json");
        JsonNode map = load("tools/surface-check/map.json");
        ((com.fasterxml.jackson.databind.node.ObjectNode) map.path("resources")).put("terminals", "com.mfxhub.sdk.NoSuchThing");
        ((com.fasterxml.jackson.databind.node.ObjectNode) map.path("methods")).put("stream.aclose", "shutDown");

        List<String> problems = missing(reference, map);

        assertTrue(problems.stream().anyMatch(p -> p.startsWith("terminals.get: no public type")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("stream.aclose: no public MfxStream.shutDown")), problems.toString());
    }
}

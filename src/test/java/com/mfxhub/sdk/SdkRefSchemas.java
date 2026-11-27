package com.mfxhub.sdk;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which contract schema each public type of the SDK represents, for the SDK reference's {@code schema} key:
 * found from the contract and from the generator's output, never written by hand.
 *
 * <p>The generator ({@code tools/GenerateModels.java}) reads the contract with resolve off, so every
 * component of {@code components.schemas} is the model {@code com.mfxhub.sdk.models.<key>}, its name the key
 * verbatim; a model whose name is not a key (an inline object such as {@code CopyCopiedAs}) is not a
 * schema. A public type of the SDK represents a schema when:
 * <ul>
 * <li>it reads a stream event: its {@code KIND} (or every one of its {@code KINDS}), or the event type
 * {@code tools/sdkref/schemas.json} gives it when the kind is its client's constant, is a key of the
 * contract's {@code x-event-payloads}, all naming one schema; or</li>
 * <li>it is a record whose components, in the contract's snake_case, are exactly the JSON properties of one
 * generated model and of no other; or</li>
 * <li>it is an enum whose wire values are exactly the values of one generated enum model and of no
 * other.</li>
 * </ul>
 * A client, a builder, an exception, an interface, a generic type (a page of anything) and a helper class
 * represent none.
 */
final class SdkRefSchemas {
    private static final Pattern SCHEMA_KEY = Pattern.compile("^ {4}(['\"]?)([A-Za-z0-9_.-]+)\\1:(?:\\s|$)");
    private static final Pattern PAYLOAD = Pattern.compile("^ {8}([a-z_.]+):$");
    private static final Pattern PAYLOAD_REF = Pattern.compile("^ {10}\\$ref: '#/components/schemas/([A-Za-z0-9_.-]+)'$");

    private SdkRefSchemas() {
    }

    /** The keys of {@code components.schemas}, read as the shared suite's checker reads them. */
    static Set<String> contractSchemas(Path openapi) throws Exception {
        Set<String> out = new LinkedHashSet<>();
        String section = null;
        for (String line : Files.readAllLines(openapi)) {
            if (line.equals("components:")) {
                section = "components";
            } else if (!line.isEmpty() && !line.startsWith(" ")) {
                section = null;
            } else if (section != null && line.startsWith("  ") && !line.startsWith("   ") && !line.isBlank()) {
                section = line.stripTrailing().equals("  schemas:") ? "schemas" : "components";
            } else if ("schemas".equals(section)) {
                Matcher key = SCHEMA_KEY.matcher(line);
                if (key.find()) {
                    out.add(key.group(2));
                }
            }
        }
        return out;
    }

    /** The stream's {@code x-event-payloads}: each event type to the schema its {@code data} is. */
    static Map<String, String> eventPayloads(Path openapi) throws Exception {
        Map<String, String> out = new TreeMap<>();
        boolean inside = false;
        String kind = null;
        for (String line : Files.readAllLines(openapi)) {
            if (line.equals("      x-event-payloads:")) {
                inside = true;
                continue;
            }
            if (!inside) {
                continue;
            }
            if (!line.startsWith("        ")) {
                break;
            }
            Matcher event = PAYLOAD.matcher(line);
            Matcher ref = PAYLOAD_REF.matcher(line);
            if (event.matches()) {
                kind = event.group(1);
            } else if (ref.matches() && kind != null) {
                out.put(kind, ref.group(1));
            }
        }
        return out;
    }

    /** The generator's model for each contract schema that has one: the key, verbatim, is the class's name. */
    static Map<String, Class<?>> models(Set<String> contract) {
        Map<String, Class<?>> out = new LinkedHashMap<>();
        for (String schema : contract) {
            try {
                out.put(schema, Class.forName("com.mfxhub.sdk.models." + schema));
            } catch (ClassNotFoundException none) {
                // A schema the generator inlines (a scalar, a union of references) has no model of its own.
            }
        }
        return out;
    }

    /** What the reference documents: every type pages.json lists, and the public types nested in it. */
    static List<Class<?>> documented(Collection<String> listed) throws Exception {
        List<Class<?>> out = new ArrayList<>();
        for (String name : listed) {
            collect(Class.forName("com.mfxhub.sdk." + name), out);
        }
        return out;
    }

    private static void collect(Class<?> type, List<Class<?>> into) {
        into.add(type);
        for (Class<?> nested : type.getDeclaredClasses()) {
            if (Modifier.isPublic(nested.getModifiers())) {
                collect(nested, into);
            }
        }
    }

    /** The type's name as the reference writes it: Outer.Inner. */
    static String name(Class<?> type) {
        return type.getEnclosingClass() == null ? type.getSimpleName() : name(type.getEnclosingClass()) + "." + type.getSimpleName();
    }

    /**
     * Each documented type that represents one schema, by its reference name, in the order given.
     *
     * @param openapi the vendored contract
     * @param documented the public types the reference lists
     * @param events a type's reference name to the event type it reads, for a type with no {@code KIND} of its own
     * @throws IllegalStateException an event type the contract gives no payload
     */
    static Map<String, String> derive(Path openapi, List<Class<?>> documented, Map<String, String> events) throws Exception {
        Set<String> contract = contractSchemas(openapi);
        Map<String, String> payloads = eventPayloads(openapi);
        Map<String, Class<?>> models = models(contract);
        Map<String, String> out = new LinkedHashMap<>();
        for (Class<?> type : documented) {
            String kind = events.get(name(type));
            String schema = kind != null ? payloads.get(kind) : schemaOf(type, payloads, models);
            if (kind != null && schema == null) {
                throw new IllegalStateException(name(type) + " reads " + kind + ", which x-event-payloads does not have");
            }
            if (schema != null) {
                out.put(name(type), schema);
            }
        }
        return out;
    }

    private static String schemaOf(Class<?> type, Map<String, String> payloads, Map<String, Class<?>> models) throws Exception {
        if (Throwable.class.isAssignableFrom(type) || type.isInterface() || type.getTypeParameters().length > 0) {
            return null;
        }
        Set<String> kinds = kinds(type);
        if (!kinds.isEmpty()) {
            Set<String> named = new java.util.HashSet<>();
            for (String kind : kinds) {
                named.add(payloads.get(kind));
            }
            return named.size() == 1 && !named.contains(null) ? named.iterator().next() : null;
        }
        if (type.isRecord()) {
            Set<String> wire = new TreeSet<>();
            for (RecordComponent component : type.getRecordComponents()) {
                wire.add(snake(component.getName()));
            }
            return only(models, model -> !model.isEnum() && properties(model).equals(wire));
        }
        if (type.isEnum()) {
            Set<String> wire = enumValues(type);
            return only(models, model -> model.isEnum() && enumValues(model).equals(wire));
        }
        return null;
    }

    private interface Test {
        boolean matches(Class<?> model) throws Exception;
    }

    /** The one schema whose model passes; null when none does or more than one does. */
    private static String only(Map<String, Class<?>> models, Test test) throws Exception {
        String found = null;
        for (Map.Entry<String, Class<?>> model : models.entrySet()) {
            if (test.matches(model.getValue())) {
                if (found != null) {
                    return null;
                }
                found = model.getKey();
            }
        }
        return found;
    }

    /** The event types a public type reads: its public static {@code KIND}, or each of its {@code KINDS}. */
    private static Set<String> kinds(Class<?> type) throws Exception {
        Set<String> out = new TreeSet<>();
        for (Field field : type.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)) {
                continue;
            }
            if (field.getName().equals("KIND") && field.getType() == String.class) {
                out.add((String) field.get(null));
            } else if (field.getName().equals("KINDS") && Set.class.isAssignableFrom(field.getType())) {
                for (Object kind : (Set<?>) field.get(null)) {
                    out.add((String) kind);
                }
            }
        }
        return out;
    }

    /** A generated model's JSON property names: its {@code JSON_PROPERTY_*} constants. */
    static Set<String> properties(Class<?> model) throws Exception {
        Set<String> out = new TreeSet<>();
        for (Field field : model.getDeclaredFields()) {
            if (field.getName().startsWith("JSON_PROPERTY_") && Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                out.add((String) field.get(null));
            }
        }
        return out;
    }

    /**
     * An enum's values as they are on the wire: as Jackson writes them, or lower case for the SDK's LowerWire enums.
     * A generated enum's UNKNOWN_DEFAULT_OPEN_API is no word of the contract and is left out.
     */
    static Set<String> enumValues(Class<?> type) {
        Set<String> out = new TreeSet<>();
        for (Object constant : type.getEnumConstants()) {
            if (MfxJson.isUnknown(constant)) {
                continue;
            }
            out.add(constant instanceof LowerWire ? ((Enum<?>) constant).name().toLowerCase(Locale.ROOT)
                    : MfxJson.MAPPER.valueToTree(constant).asText());
        }
        return out;
    }

    /** A Java name in the contract's snake_case: amountUsd is amount_usd. */
    static String snake(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
}

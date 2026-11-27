package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import java.util.Iterator;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A request body checked against the contract before it is sent: it is read as the generated request
 * model, and every name the caller sent must survive that, so a misspelt setting is refused here rather
 * than dropped, which would be a follow with a setting nobody chose. A value the model cannot hold (a
 * word that is not one of an enum's) is refused too.
 */
final class Contract {
    /**
     * The SDK's mapper with the models' catch-all for undeclared names turned off. The generator gives
     * every model one, but each schema this checks says {@code additionalProperties: false}, so a name the
     * contract does not have is dropped here, as the models drop it, and then refused.
     */
    private static final ObjectMapper STRICT = MfxJson.MAPPER.copy().registerModule(MfxJson.unsent()).setAnnotationIntrospector(new JacksonAnnotationIntrospector() {
        private static final long serialVersionUID = 1L;

        @Override
        public Boolean hasAnySetter(Annotated annotated) {
            return Boolean.FALSE;
        }

        @Override
        public Boolean hasAnyGetter(Annotated annotated) {
            return Boolean.FALSE;
        }
    });

    private Contract() {
    }

    /**
     * The body, ready to send.
     *
     * @throws IllegalArgumentException a name the contract does not have, a value it cannot hold, or a double
     */
    static Map<String, @Nullable Object> body(String name, Class<?> model, Body fields) {
        @SuppressWarnings("unchecked")
        Map<String, @Nullable Object> sent = (Map<String, @Nullable Object>) Wire.of(fields.build());
        if (sent == null) {
            throw new IllegalArgumentException(name + " has no body");
        }
        JsonNode given = MfxJson.MAPPER.valueToTree(sent);
        JsonNode kept;
        try {
            kept = STRICT.valueToTree(STRICT.treeToValue(given, model));
        } catch (java.io.IOException | IllegalArgumentException misfit) {
            throw new IllegalArgumentException(name + " does not fit the contract: " + misfit.getMessage(), misfit);
        }
        check(name, given, kept);
        return sent;
    }

    private static void check(String name, JsonNode sent, @Nullable JsonNode kept) {
        if (sent.isObject() && kept != null && kept.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = sent.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (field.getValue().isNull()) {
                    continue; // sent as null on purpose, to clear it; the model writes no null back
                }
                if (!kept.has(field.getKey())) {
                    throw new IllegalArgumentException(name + "." + field.getKey() + " is not a field the contract has");
                }
                check(name + "." + field.getKey(), field.getValue(), kept.get(field.getKey()));
            }
        } else if (sent.isArray() && kept != null && kept.isArray()) {
            for (int i = 0; i < Math.min(sent.size(), kept.size()); i++) {
                check(name + "[" + i + "]", sent.get(i), kept.get(i));
            }
        }
    }
}

package com.mfxhub.sdk;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * How every body is read and written: the API's own field names (the generated models carry them),
 * times as ISO 8601 text, absent fields left out rather than written as null, a field this version
 * does not know yet read past rather than refused, and every {@link BigDecimal} exact, as a JSON string.
 *
 * <p>A decimal is a JSON string such as {@code "1.10450"}, read and written without passing through
 * binary floating point. A JSON number token is refused where a {@code BigDecimal} is read, because a
 * number has already been through a double in whatever wrote it: {@code 0.07 * 3} is
 * {@code 0.21000000000000002}. The scale is kept, so {@code "1.10"} goes back out as {@code "1.10"}, and
 * a decimal is always written plainly, never with an exponent. Text that is not a plain decimal (an
 * exponent, a separator, a space) is refused.
 */
public final class MfxJson {
    /** A decimal as the contract writes one (its {@code CopyDecimal} pattern). */
    private static final Pattern DECIMAL = Pattern.compile("^-?[0-9]+(\\.[0-9]+)?$");

    static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .addModule(new SimpleModule("mfx-exact-decimals")
                    .addSerializer(BigDecimal.class, new ExactWriter())
                    .addDeserializer(BigDecimal.class, new ExactReader()))
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();

    /**
     * The member every generated enum reads a word it does not know as, a word a newer API added: the
     * generator's own name for it ({@code enumUnknownDefaultCase}). An enum of numbers names it
     * {@code NUMBER_unknown_default_open_api}.
     */
    static final String UNKNOWN = "UNKNOWN_DEFAULT_OPEN_API";

    /**
     * {@link #MAPPER} for a request body: an enum's {@link #UNKNOWN} is refused, never written, because a
     * request carries only words the contract lists.
     */
    static final ObjectMapper REQUESTS = MAPPER.copy().registerModule(unsent());

    private MfxJson() {
    }

    /** Whether {@code value} is a generated enum's {@link #UNKNOWN}, which no word of the contract names. */
    static boolean isUnknown(@Nullable Object value) {
        return value instanceof Enum<?> constant && constant.name().toUpperCase(java.util.Locale.ROOT).endsWith(UNKNOWN);
    }

    /** The refusal for {@link #UNKNOWN} where a word must be sent. */
    static String unsentMessage(Enum<?> constant) {
        return constant.getDeclaringClass().getSimpleName() + "." + UNKNOWN + " stands for a word this version of the SDK"
                + " does not know; it is read from an answer and never sent";
    }

    /** A module that refuses to write an enum's {@link #UNKNOWN}, for the mappers a request is written with. */
    static SimpleModule unsent() {
        return new SimpleModule("mfx-unknown-unsent").setSerializerModifier(new BeanSerializerModifier() {
            private static final long serialVersionUID = 1L;

            @Override
            public JsonSerializer<?> modifySerializer(SerializationConfig config, BeanDescription description,
                    JsonSerializer<?> serializer) {
                return description.getBeanClass().isEnum() ? new Unsent(serializer) : serializer;
            }

            @Override
            public JsonSerializer<?> modifyEnumSerializer(SerializationConfig config, JavaType type,
                    BeanDescription description, JsonSerializer<?> serializer) {
                return new Unsent(serializer);
            }
        });
    }

    /** An enum's own serializer, refusing {@link #UNKNOWN}. */
    private static final class Unsent extends JsonSerializer<Object> implements ContextualSerializer {
        private final JsonSerializer<Object> delegate;

        @SuppressWarnings("unchecked")
        Unsent(JsonSerializer<?> delegate) {
            this.delegate = (JsonSerializer<Object>) delegate;
        }

        @Override
        public JsonSerializer<?> createContextual(SerializerProvider provider, @Nullable BeanProperty property)
                throws JsonMappingException {
            return delegate instanceof ContextualSerializer contextual
                    ? new Unsent(contextual.createContextual(provider, property))
                    : this;
        }

        @Override
        public void serialize(Object value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            if (value instanceof Enum<?> constant && isUnknown(constant)) {
                throw JsonMappingException.from(generator, unsentMessage(constant));
            }
            delegate.serialize(value, generator, provider);
        }

        @Override
        public Class<Object> handledType() {
            return delegate.handledType();
        }
    }

    /**
     * A mapper configured as the SDK's own, for reading and writing the models yourself.
     *
     * @return a copy: changing it does not change how the SDK reads and writes
     */
    public static ObjectMapper mapper() {
        return MAPPER.copy();
    }

    /**
     * A decimal from the API's text, exactly.
     *
     * @param text the text, such as {@code 1.10450}
     * @return the decimal, its scale kept
     * @throws IllegalArgumentException it is not a plain decimal as the API writes one
     */
    public static BigDecimal decimal(String text) {
        if (!DECIMAL.matcher(text).matches()) {
            throw new IllegalArgumentException("'" + text + "' is not a decimal as the API writes one, such as \"1.10450\"");
        }
        return new BigDecimal(text);
    }

    private static final class ExactWriter extends JsonSerializer<BigDecimal> {
        @Override
        public void serialize(BigDecimal value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            generator.writeString(value.toPlainString());
        }
    }

    private static final class ExactReader extends JsonDeserializer<BigDecimal> {
        @Override
        public BigDecimal deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.currentToken() != JsonToken.VALUE_STRING) {
                return (BigDecimal) context.handleUnexpectedToken(BigDecimal.class, parser.currentToken(), parser,
                        "a decimal is a JSON string such as \"1.10450\", not %s: a number token has already been"
                                + " through binary floating point", parser.currentToken());
            }
            String text = parser.getText();
            if (!DECIMAL.matcher(text).matches()) {
                return (BigDecimal) context.handleWeirdStringValue(BigDecimal.class, text,
                        "not a decimal as the API writes one, such as \"1.10450\"");
            }
            return new BigDecimal(text);
        }
    }
}

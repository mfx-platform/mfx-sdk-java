import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openapitools.codegen.ClientOptInput;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.CodegenConfig;
import org.openapitools.codegen.CodegenConfigLoader;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.config.GlobalSettings;

/**
 * Writes the SDK's models from the public contract: OpenAPI Generator's {@code java}
 * generator, models only, Jackson, the same options on every machine.
 *
 * <pre>
 *   java -cp openapi-generator-cli.jar tools/GenerateModels.java spec/openapi.yaml target/generated-sources/openapi tools/templates
 * </pre>
 *
 * <p>Why not the generator's Maven plugin: it parses the contract with {@code resolve=true}, and its
 * OpenAPI 3.1 parser then dereferences every {@code $ref} into a copy before the models are made. A
 * copy that matches a component takes the name of whichever use the generator met first, so
 * {@code TenantTerminalPage.items} became {@code List<CreateTerminal202Response>}; an enum or a
 * nullable reference became an empty class; and which happened depended on the machine (a path with
 * a space in it stopped the dereferencing). Here the contract is read from its text with resolve off,
 * so every {@code $ref} stays a reference to its component, and the models are the components, named
 * as the contract names them, wherever this runs. The contract has no external reference to resolve.
 */
public final class GenerateModels {
    private GenerateModels() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("usage: GenerateModels <openapi.yaml> <output directory> <template directory>");
        }
        String text = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        SwaggerParseResult parsed = new OpenAPIParser().readContents(text, null, options);
        OpenAPI contract = parsed.getOpenAPI();
        if (contract == null) {
            throw new IllegalStateException("the contract did not parse: " + parsed.getMessages());
        }
        int simplified = 0;
        for (Schema<?> schema : contract.getComponents().getSchemas().values()) {
            simplified += nullableReferences(schema);
        }

        CodegenConfig config = CodegenConfigLoader.forName("java");
        config.setLibrary("resttemplate");
        config.setOutputDir(args[1]);
        // The generator's own templates, but for the two enum templates in tools/templates, each changed in
        // one place (see their header): a nullable enum reads a word it does not know as
        // UNKNOWN_DEFAULT_OPEN_API too, not as null.
        // DECISION: null would read as the field being absent, and an unknown word is not an absent one; the
        // generator's own templates do that for a nullable enum, so the two are overridden, not configured.
        config.additionalProperties().put(CodegenConstants.TEMPLATE_DIR, args[2]);
        Map<String, Object> properties = config.additionalProperties();
        properties.put(CodegenConstants.MODEL_PACKAGE, "com.mfxhub.sdk.models");
        properties.put(CodegenConstants.INVOKER_PACKAGE, "com.mfxhub.sdk.models.support");
        properties.put(CodegenConstants.HIDE_GENERATION_TIMESTAMP, true);
        properties.put("dateLibrary", "java8");
        properties.put("openApiNullable", false);
        properties.put("useJakartaEe", true);
        properties.put(CodegenConstants.SERIALIZATION_LIBRARY, "jackson");
        // A word a newer API adds to an enum reads as UNKNOWN_DEFAULT_OPEN_API, which the generator
        // adds to every enum, instead of failing the call: fromValue returns it for a word it does not
        // know. A request refuses to send it (MfxJson.unsent, Query.text).
        properties.put(CodegenConstants.ENUM_UNKNOWN_DEFAULT_CASE, true);
        // A JSON number in this contract is never money: every price, volume and amount is a decimal
        // string (OrderPlace). So a number is a Double, as in .NET, and BigDecimal is kept for the exact
        // decimals MfxJson reads from strings, refusing a number token.
        config.typeMapping().put("number", "Double");
        // Models only: no API classes, no supporting files, no tests and no docs.
        GlobalSettings.setProperty(CodegenConstants.MODELS, "");
        GlobalSettings.setProperty(CodegenConstants.MODEL_TESTS, "false");
        GlobalSettings.setProperty(CodegenConstants.MODEL_DOCS, "false");
        ClientOptInput input = new ClientOptInput().openAPI(contract).config(config);

        DefaultGenerator generator = new DefaultGenerator();
        generator.setGenerateMetadata(false);
        List<java.io.File> written = generator.opts(input).generate();
        System.out.println("GenerateModels: " + written.size() + " files from " + args[0] + "; "
                + simplified + " nullable references read as their reference");
    }

    /**
     * {@code oneOf: [{$ref: X}, {type: "null"}]} is the contract's way to say "an X, or null". The
     * generator makes an empty class of it, which cannot hold an X; a Java field of type X is already
     * nullable, so it is read as the reference alone. Nothing else in the contract is changed.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int nullableReferences(Schema schema) {
        int count = 0;
        for (boolean oneOf : new boolean[] {true, false}) {
            List<Schema<?>> choices = oneOf ? schema.getOneOf() : schema.getAnyOf();
            if (choices != null && choices.size() == 2) {
                Schema<?> other = isNull(choices.get(0)) ? choices.get(1) : isNull(choices.get(1)) ? choices.get(0) : null;
                if (other != null && other.get$ref() != null) {
                    schema.set$ref(other.get$ref());
                    schema.setNullable(true);
                    if (oneOf) {
                        schema.setOneOf(null);
                    } else {
                        schema.setAnyOf(null);
                    }
                    count++;
                }
            }
        }
        for (Schema<?> child : children(schema)) {
            count += nullableReferences(child);
        }
        return count;
    }

    private static boolean isNull(Schema<?> schema) {
        return schema.get$ref() == null && ("null".equals(schema.getType())
                || (schema.getTypes() != null && schema.getTypes().equals(Set.of("null"))));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Schema<?>> children(Schema schema) {
        List<Schema<?>> children = new ArrayList<>();
        if (schema.getProperties() != null) {
            children.addAll(((Map<String, Schema<?>>) schema.getProperties()).values());
        }
        if (schema.getItems() != null) {
            children.add(schema.getItems());
        }
        if (schema.getAdditionalProperties() instanceof Schema<?> extra) {
            children.add(extra);
        }
        for (List<Schema<?>> composed : new List[] {schema.getAllOf(), schema.getOneOf(), schema.getAnyOf()}) {
            if (composed != null) {
                children.addAll(composed);
            }
        }
        return children;
    }
}

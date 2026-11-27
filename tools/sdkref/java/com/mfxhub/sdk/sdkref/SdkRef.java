package com.mfxhub.sdk.sdkref;

import com.sun.source.doctree.AttributeTree;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.EndElementTree;
import com.sun.source.doctree.EntityTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.LiteralTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.doctree.ReferenceTree;
import com.sun.source.doctree.ReturnTree;
import com.sun.source.doctree.StartElementTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.doctree.ThrowsTree;
import com.sun.source.doctree.ValueTree;
import com.sun.source.doctree.DeprecatedTree;
import com.sun.source.util.DocTreePath;
import com.sun.source.util.DocTrees;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.util.Elements.Origin;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.DiagnosticCollector;
import javax.tools.DocumentationTool;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;

/**
 * Writes {@code docs/sdkref.json}, the SDK reference in the shared format {@code mfx-sdkref/1}, from the
 * public API of the SDK's own source: a javadoc doclet that reads every public type and member of the
 * package, its signature and its doc comment, and writes the comment as Markdown (a link tag becomes
 * {@code [Name](#anchor)}, a code tag a code span, {@code <p>} a paragraph, and the block tags the
 * member's params, returns and raises). No raw HTML is left: what is not Markdown is dropped.
 *
 * <p>The pages each type belongs to, their intros and each method's contract operation are this
 * directory's {@code pages.json} and {@code operations.json}; an error type's code is given by the caller,
 * from the SDK's own table, and the contract schema a type represents is given by the caller too. The output
 * depends on nothing but those and the source, so two runs write the same bytes.
 */
public final class SdkRef {
    /** The format this writes. */
    public static final String FORMAT = "mfx-sdkref/1";

    /**
     * What one generation reads.
     *
     * @param sources the SDK's source root (the package's directory is found under it)
     * @param packageName the documented package
     * @param classpath what the source compiles against (the generated models, Jackson, JSpecify)
     * @param pages {@code pages.json}: each page's id, intro and top-level types, in display order
     * @param operations {@code operations.json}: {@code Type.method} to the contract operationId it calls
     * @param codes an error type's simple name to the API error code it maps
     * @param schemas a type's name ({@code Outer.Inner} when nested) to the contract schema it represents, a key
     *     of {@code components.schemas} verbatim
     * @param header the top-level fields before {@code pages}: format, language, package, version, ...
     */
    public record Config(Path sources, String packageName, String classpath, Map<String, Object> pages,
            Map<String, String> operations, Map<String, String> codes, Map<String, String> schemas, Map<String, Object> header) {
    }

    private static final Object LOCK = new Object();
    private static Config current;
    private static String output;
    private static List<String> problems;

    private SdkRef() {
    }

    /**
     * Generates the file's text.
     *
     * @param config what to read
     * @return the JSON text, two-space indented, LF line ends, a newline at the end
     * @throws IllegalStateException javadoc failed, or the source has a public type no page names
     */
    public static String generate(Config config) throws IOException {
        synchronized (LOCK) {
            current = config;
            output = null;
            problems = new ArrayList<>();
            try {
                DocumentationTool tool = ToolProvider.getSystemDocumentationTool();
                DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
                try (StandardJavaFileManager files = tool.getStandardFileManager(diagnostics, Locale.ROOT, java.nio.charset.StandardCharsets.UTF_8);
                        Stream<Path> walk = Files.walk(config.sources().resolve(config.packageName().replace('.', '/')), 1)) {
                    List<Path> sources = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
                    Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromPaths(sources);
                    List<String> options = List.of("-classpath", config.classpath(), "-encoding", "UTF-8", "-quiet",
                            "--release", "21");
                    StringWriter log = new StringWriter();
                    Boolean ok = tool.getTask(log, files, diagnostics, Generator.class,
                            options, units).call();
                    if (!Boolean.TRUE.equals(ok) || output == null) {
                        throw new IllegalStateException("javadoc failed: " + log + diagnostics.getDiagnostics());
                    }
                }
                if (!problems.isEmpty()) {
                    throw new IllegalStateException(String.join("\n", problems));
                }
                return output;
            } finally {
                current = null;
            }
        }
    }

    /** The doclet javadoc runs: it reads the package and leaves the JSON in {@link SdkRef#output}. */
    public static final class Generator implements Doclet {
        /** Javadoc makes it. */
        public Generator() {
        }

        @Override
        public void init(Locale locale, Reporter reporter) {
        }

        @Override
        public String getName() {
            return "sdkref";
        }

        @Override
        public Set<? extends Option> getSupportedOptions() {
            return Set.of();
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }

        @Override
        public boolean run(DocletEnvironment environment) {
            output = new Reader(environment, current, problems).write();
            return true;
        }
    }

    /** One run over the package. */
    private static final class Reader {
        private final DocTrees trees;
        private final Elements elements;
        private final Types types;
        private final Config config;
        private final List<String> problems;
        private final Map<String, TypeElement> byName = new LinkedHashMap<>();
        private final Map<TypeElement, String> pageOf = new HashMap<>();
        private final Map<Element, String> anchors = new HashMap<>();

        Reader(DocletEnvironment environment, Config config, List<String> problems) {
            this.trees = environment.getDocTrees();
            this.elements = environment.getElementUtils();
            this.types = environment.getTypeUtils();
            this.config = config;
            this.problems = problems;
            for (TypeElement type : ElementFilter.typesIn(environment.getIncludedElements())) {
                if (type.getNestingKind().isNested() || !isPublic(type)) {
                    continue;
                }
                PackageElement pkg = elements.getPackageOf(type);
                if (pkg.getQualifiedName().contentEquals(config.packageName())) {
                    byName.put(type.getSimpleName().toString(), type);
                }
            }
        }

        private static boolean isPublic(Element element) {
            return element.getModifiers().contains(Modifier.PUBLIC);
        }

        @SuppressWarnings("unchecked")
        private List<Map<String, Object>> pageList() {
            return (List<Map<String, Object>>) config.pages().get("pages");
        }

        @SuppressWarnings("unchecked")
        String write() {
            // Every page's types, top level then nested, and every anchor first: a link may point ahead.
            Map<String, List<TypeElement>> listed = new LinkedHashMap<>();
            for (Map<String, Object> page : pageList()) {
                String id = (String) page.get("id");
                List<TypeElement> all = new ArrayList<>();
                for (String name : (List<String>) page.get("types")) {
                    TypeElement type = byName.get(name);
                    if (type == null) {
                        problems.add("pages.json lists " + name + " on " + id + ", which is not a public type of " + config.packageName());
                        continue;
                    }
                    if (pageOf.containsKey(type)) {
                        problems.add(name + " is on two pages");
                    }
                    collect(type, all);
                }
                for (TypeElement type : all) {
                    pageOf.put(type, id);
                }
                listed.put(id, all);
            }
            for (String name : byName.keySet()) {
                if (!pageOf.containsKey(byName.get(name))) {
                    problems.add(name + " is public and on no page of pages.json");
                }
            }
            for (Map.Entry<String, List<TypeElement>> page : listed.entrySet()) {
                for (TypeElement type : page.getValue()) {
                    String typeAnchor = page.getKey() + "." + anchorName(type);
                    anchors.put(type, typeAnchor);
                    // One anchor per name: a method's overloads are one member of the reference.
                    for (Element member : members(type)) {
                        anchors.put(member, typeAnchor + "." + memberName(member));
                    }
                }
            }
            for (String key : config.schemas().keySet()) {
                if (anchors.keySet().stream().noneMatch(e -> e instanceof TypeElement t && name(t).equals(key))) {
                    problems.add("a schema is given for " + key + ", which is not a type on a page");
                }
            }
            for (String key : config.operations().keySet()) {
                int dot = key.lastIndexOf('.');
                String typeName = dot < 0 ? key : key.substring(0, dot);
                String method = dot < 0 ? "" : key.substring(dot + 1);
                boolean found = anchors.keySet().stream().anyMatch(e -> e.getKind() == ElementKind.METHOD
                        && e.getSimpleName().contentEquals(method) && name((TypeElement) e.getEnclosingElement()).equals(typeName));
                if (!found) {
                    problems.add("operations.json names " + key + ", which is not a public method");
                }
            }

            Map<String, Object> root = new LinkedHashMap<>(config.header());
            List<Object> pages = new ArrayList<>();
            for (Map<String, Object> page : pageList()) {
                String id = (String) page.get("id");
                List<TypeElement> all = listed.get(id);
                if (all.isEmpty()) {
                    continue;
                }
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("id", id);
                out.put("intro", page.get("intro"));
                List<Object> typesOut = new ArrayList<>();
                for (TypeElement type : all) {
                    typesOut.add(type(type));
                }
                out.put("types", typesOut);
                pages.add(out);
            }
            root.put("pages", pages);
            StringBuilder text = new StringBuilder();
            Json.write(root, text, 0);
            return text.append('\n').toString();
        }

        private void collect(TypeElement type, List<TypeElement> into) {
            into.add(type);
            for (TypeElement nested : ElementFilter.typesIn(type.getEnclosedElements())) {
                if (isPublic(nested)) {
                    collect(nested, into);
                }
            }
        }

        /** The type's name as Java writes it from outside: Outer.Inner. */
        private static String name(TypeElement type) {
            Element enclosing = type.getEnclosingElement();
            if (enclosing instanceof TypeElement outer) {
                return name(outer) + "." + type.getSimpleName();
            }
            return type.getSimpleName().toString();
        }

        private String memberName(Element member) {
            return member.getKind() == ElementKind.CONSTRUCTOR ? member.getEnclosingElement().getSimpleName().toString()
                    : member.getSimpleName().toString();
        }

        /** A nested type's anchor segment: Outer_Inner, since an anchor's segments are the page, the type and the member. */
        private static String anchorName(TypeElement type) {
            return name(type).replace('.', '_');
        }

        /** The public members in declaration order: what the reader can call or name. */
        private List<Element> members(TypeElement type) {
            List<Element> out = new ArrayList<>();
            Set<Element> accessors = new java.util.HashSet<>();
            for (RecordComponentElement component : ElementFilter.recordComponentsIn(type.getEnclosedElements())) {
                out.add(component);
                if (component.getAccessor() != null) {
                    accessors.add(component.getAccessor());
                }
            }
            for (Element member : type.getEnclosedElements()) {
                if (!isPublic(member) && member.getKind() != ElementKind.ENUM_CONSTANT) {
                    continue;
                }
                // Implicit members (an enum's values and valueOf, a record's equals) have no source of their own.
                if (elements.getOrigin(member) != Origin.EXPLICIT || trees.getPath(member) == null || accessors.contains(member)) {
                    continue;
                }
                switch (member.getKind()) {
                    case ENUM_CONSTANT, FIELD, METHOD -> out.add(member);
                    case CONSTRUCTOR -> {
                        if (type.getKind() != ElementKind.RECORD || !isCanonical(type, (ExecutableElement) member)) {
                            out.add(member);
                        }
                    }
                    default -> {
                    }
                }
            }
            // An override with nothing of its own to say (toString, equals, hashCode) is not part of the reference.
            out.removeIf(m -> m.getKind() == ElementKind.METHOD && overridesObject((ExecutableElement) m) && comment(m) == null);
            return out;
        }

        private boolean isCanonical(TypeElement record, ExecutableElement constructor) {
            List<? extends RecordComponentElement> components = ElementFilter.recordComponentsIn(record.getEnclosedElements());
            if (components.size() != constructor.getParameters().size()) {
                return false;
            }
            for (int i = 0; i < components.size(); i++) {
                if (!types.isSameType(types.erasure(components.get(i).asType()), types.erasure(constructor.getParameters().get(i).asType()))) {
                    return false;
                }
            }
            return true;
        }

        private static boolean overridesObject(ExecutableElement method) {
            String name = method.getSimpleName().toString();
            int arity = method.getParameters().size();
            return ((name.equals("toString") || name.equals("hashCode")) && arity == 0) || (name.equals("equals") && arity == 1);
        }

        private DocCommentTree comment(Element element) {
            return trees.getDocCommentTree(element);
        }

        // ---------------------------------------------------------------- types

        private Map<String, Object> type(TypeElement type) {
            Map<String, Object> out = new LinkedHashMap<>();
            String simple = type.getSimpleName().toString();
            boolean error = isError(type);
            out.put("anchor", anchors.get(type));
            out.put("kind", error ? "error" : switch (type.getKind()) {
                case RECORD -> "record";
                case ENUM -> "enum";
                case INTERFACE, ANNOTATION_TYPE -> "interface";
                default -> "class";
            });
            out.put("name", name(type));
            out.put("signature", typeSignature(type));
            DocCommentTree comment = comment(type);
            out.put("doc", comment == null ? "" : markdown(comment.getFullBody(), type));
            List<String> supers = supertypes(type);
            if (!supers.isEmpty()) {
                out.put("extends", supers);
            }
            if (error && config.codes().containsKey(simple)) {
                out.put("code", config.codes().get(simple));
            }
            String schema = config.schemas().get(name(type));
            if (schema != null) {
                out.put("schema", schema);
            }
            List<Object> members = new ArrayList<>();
            Map<String, String> recordParams = type.getKind() == ElementKind.RECORD ? paramDocs(comment, type) : Map.of();
            Map<String, List<Element>> byName = new LinkedHashMap<>();
            for (Element member : members(type)) {
                byName.computeIfAbsent(memberName(member), k -> new ArrayList<>()).add(member);
            }
            for (Map.Entry<String, List<Element>> group : byName.entrySet()) {
                List<Element> all = group.getValue();
                if (all.stream().map(Element::getKind).distinct().count() > 1) {
                    problems.add(name(type) + "." + group.getKey() + " names members of different kinds");
                }
                if (all.size() == 1) {
                    members.add(member(type, all.get(0), recordParams));
                    continue;
                }
                // Overloads: the fullest is the member (its signature, params, returns and raises); the doc lists the others.
                Element fullest = all.get(0);
                for (Element candidate : all) {
                    if (((ExecutableElement) candidate).getParameters().size() > ((ExecutableElement) fullest).getParameters().size()) {
                        fullest = candidate;
                    }
                }
                Map<String, Object> merged = member(type, fullest, recordParams);
                List<String> others = new ArrayList<>();
                for (Element candidate : all) {
                    if (candidate != fullest) {
                        others.add(code(executableSignature((ExecutableElement) candidate)));
                    }
                }
                String doc = (String) merged.get("doc");
                String also = "Also callable as " + String.join(", ", others) + ".";
                merged.put("doc", doc.isEmpty() ? also : doc + "\n\n" + also);
                members.add(merged);
            }
            if (!members.isEmpty()) {
                out.put("members", members);
            }
            return out;
        }

        private boolean isError(TypeElement type) {
            TypeMirror throwable = elements.getTypeElement("java.lang.Throwable").asType();
            return types.isSubtype(type.asType(), throwable);
        }

        /** The supertypes a reader can name: the superclass unless Object, Record or Enum, and the public interfaces. */
        private List<String> supertypes(TypeElement type) {
            List<String> out = new ArrayList<>();
            TypeMirror superclass = type.getSuperclass();
            if (superclass.getKind() == TypeKind.DECLARED) {
                String name = ((TypeElement) ((DeclaredType) superclass).asElement()).getQualifiedName().toString();
                if (!name.equals("java.lang.Object") && !name.equals("java.lang.Record") && !name.equals("java.lang.Enum")) {
                    out.add(simple(types.erasure(superclass), false));
                }
            }
            for (TypeMirror face : visibleInterfaces(type)) {
                out.add(simple(types.erasure(face), false));
            }
            return out;
        }

        private List<TypeMirror> visibleInterfaces(TypeElement type) {
            List<TypeMirror> out = new ArrayList<>();
            for (TypeMirror face : type.getInterfaces()) {
                if (isPublic(((DeclaredType) face).asElement())) {
                    out.add(face);
                }
            }
            return out;
        }

        private String typeSignature(TypeElement type) {
            StringBuilder out = new StringBuilder();
            Set<Modifier> modifiers = type.getModifiers();
            boolean nested = type.getNestingKind().isNested();
            ElementKind kind = type.getKind();
            if (modifiers.contains(Modifier.SEALED)) {
                out.append("sealed ");
            }
            if (modifiers.contains(Modifier.NON_SEALED)) {
                out.append("non-sealed ");
            }
            if (modifiers.contains(Modifier.ABSTRACT) && kind == ElementKind.CLASS) {
                out.append("abstract ");
            }
            if (nested && modifiers.contains(Modifier.STATIC) && kind == ElementKind.CLASS) {
                out.append("static ");
            }
            if (modifiers.contains(Modifier.FINAL) && kind == ElementKind.CLASS) {
                out.append("final ");
            }
            out.append(switch (kind) {
                case RECORD -> "record ";
                case ENUM -> "enum ";
                case INTERFACE -> "interface ";
                case ANNOTATION_TYPE -> "@interface ";
                default -> "class ";
            });
            out.append(type.getSimpleName()).append(typeParameters(type.getTypeParameters()));
            if (kind == ElementKind.RECORD) {
                out.append(ElementFilter.recordComponentsIn(type.getEnclosedElements()).stream()
                        .map(c -> simple(c.asType(), true) + " " + c.getSimpleName()).collect(Collectors.joining(", ", "(", ")")));
            }
            TypeMirror superclass = type.getSuperclass();
            if (kind == ElementKind.CLASS && superclass.getKind() == TypeKind.DECLARED
                    && !((TypeElement) ((DeclaredType) superclass).asElement()).getQualifiedName().contentEquals("java.lang.Object")) {
                out.append(" extends ").append(simple(superclass, true));
            }
            List<TypeMirror> faces = visibleInterfaces(type);
            if (!faces.isEmpty()) {
                out.append(kind == ElementKind.INTERFACE ? " extends " : " implements ")
                        .append(faces.stream().map(f -> simple(f, true)).collect(Collectors.joining(", ")));
            }
            List<? extends TypeMirror> permitted = type.getPermittedSubclasses();
            if (!permitted.isEmpty()) {
                out.append(" permits ").append(permitted.stream().map(p -> simple(p, false)).collect(Collectors.joining(", ")));
            }
            return out.toString();
        }

        private String typeParameters(List<? extends TypeParameterElement> parameters) {
            if (parameters.isEmpty()) {
                return "";
            }
            return parameters.stream().map(p -> {
                List<? extends TypeMirror> bounds = p.getBounds();
                boolean plain = bounds.size() == 1 && bounds.get(0).toString().equals("java.lang.Object");
                return plain ? p.getSimpleName().toString()
                        : p.getSimpleName() + " extends " + bounds.stream().map(b -> simple(b, true)).collect(Collectors.joining(" & "));
            }).collect(Collectors.joining(", ", "<", ">"));
        }

        /** A type as Java writes it in a signature, with simple names and its type-use annotations. */
        String simple(TypeMirror type, boolean annotations) {
            String prefix = "";
            if (annotations) {
                StringBuilder notes = new StringBuilder();
                for (AnnotationMirror note : type.getAnnotationMirrors()) {
                    notes.append('@').append(note.getAnnotationType().asElement().getSimpleName()).append(' ');
                }
                prefix = notes.toString();
            }
            return prefix + switch (type.getKind()) {
                case DECLARED -> {
                    DeclaredType declared = (DeclaredType) type;
                    String base = name((TypeElement) declared.asElement());
                    List<? extends TypeMirror> arguments = declared.getTypeArguments();
                    yield arguments.isEmpty() ? base
                            : base + arguments.stream().map(a -> simple(a, annotations)).collect(Collectors.joining(", ", "<", ">"));
                }
                case ARRAY -> simple(((ArrayType) type).getComponentType(), annotations) + "[]";
                case TYPEVAR -> ((TypeVariable) type).asElement().getSimpleName().toString();
                case WILDCARD -> {
                    WildcardType wildcard = (WildcardType) type;
                    if (wildcard.getExtendsBound() != null) {
                        yield "? extends " + simple(wildcard.getExtendsBound(), annotations);
                    }
                    yield wildcard.getSuperBound() != null ? "? super " + simple(wildcard.getSuperBound(), annotations) : "?";
                }
                default -> type.getKind() == TypeKind.VOID ? "void" : stripAnnotations(type.toString());
            };
        }

        private static String stripAnnotations(String text) {
            return text.replaceAll("@[\\w.]+(\\([^)]*\\))?\\s+", "");
        }

        // ---------------------------------------------------------------- members

        private Map<String, Object> member(TypeElement owner, Element member, Map<String, String> recordParams) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("anchor", anchors.get(member));
            DocCommentTree comment = comment(member);
            switch (member.getKind()) {
                case RECORD_COMPONENT -> {
                    RecordComponentElement component = (RecordComponentElement) member;
                    ExecutableElement accessor = component.getAccessor();
                    DocCommentTree own = accessor == null ? null : comment(accessor);
                    out.put("kind", "property");
                    out.put("name", member.getSimpleName().toString());
                    out.put("signature", simple(component.asType(), true) + " " + member.getSimpleName() + "()");
                    out.put("doc", own != null ? markdown(own.getFullBody(), accessor)
                            : recordParams.getOrDefault(member.getSimpleName().toString(), ""));
                }
                case ENUM_CONSTANT -> {
                    out.put("kind", "value");
                    out.put("name", member.getSimpleName().toString());
                    out.put("signature", member.getSimpleName().toString());
                    out.put("doc", comment == null ? "" : markdown(comment.getFullBody(), member));
                }
                case FIELD -> {
                    VariableElement field = (VariableElement) member;
                    out.put("kind", "field");
                    out.put("name", member.getSimpleName().toString());
                    StringBuilder signature = new StringBuilder();
                    if (field.getModifiers().contains(Modifier.STATIC)) {
                        signature.append("static ");
                    }
                    if (field.getModifiers().contains(Modifier.FINAL)) {
                        signature.append("final ");
                    }
                    signature.append(simple(field.asType(), true)).append(' ').append(field.getSimpleName());
                    Object constant = field.getConstantValue();
                    if (constant != null) {
                        signature.append(" = ").append(elements.getConstantExpression(constant));
                    }
                    out.put("signature", signature.toString());
                    out.put("doc", comment == null ? "" : markdown(comment.getFullBody(), member));
                }
                default -> executable(owner, (ExecutableElement) member, comment, out);
            }
            if (member.getKind() != ElementKind.RECORD_COMPONENT) {
                deprecated(member, comment, out);
            }
            return out;
        }

        private void executable(TypeElement owner, ExecutableElement method, DocCommentTree comment, Map<String, Object> out) {
            boolean constructor = method.getKind() == ElementKind.CONSTRUCTOR;
            out.put("kind", constructor ? "constructor" : "method");
            out.put("name", memberName(method));
            out.put("signature", executableSignature(method));
            List<? extends VariableElement> parameters = method.getParameters();
            DocCommentTree doc = comment != null ? comment : inherited(owner, method);
            out.put("doc", doc == null ? "" : markdown(doc.getFullBody(), method));
            restOfExecutable(owner, method, doc, parameters, constructor, out);
        }

        private static String code(String text) {
            String fence = text.contains("`") ? "``" : "`";
            return fence + text + fence;
        }

        private String executableSignature(ExecutableElement method) {
            boolean constructor = method.getKind() == ElementKind.CONSTRUCTOR;
            StringBuilder signature = new StringBuilder();
            Set<Modifier> modifiers = method.getModifiers();
            if (modifiers.contains(Modifier.STATIC)) {
                signature.append("static ");
            }
            if (modifiers.contains(Modifier.DEFAULT)) {
                signature.append("default ");
            }
            String generics = typeParameters(method.getTypeParameters());
            if (!generics.isEmpty()) {
                signature.append(generics).append(' ');
            }
            if (!constructor) {
                signature.append(simple(method.getReturnType(), true)).append(' ');
            }
            signature.append(memberName(method)).append('(');
            List<? extends VariableElement> parameters = method.getParameters();
            for (int i = 0; i < parameters.size(); i++) {
                VariableElement parameter = parameters.get(i);
                String typeText = simple(parameter.asType(), true);
                if (method.isVarArgs() && i == parameters.size() - 1 && typeText.endsWith("[]")) {
                    typeText = typeText.substring(0, typeText.length() - 2) + "...";
                }
                signature.append(i == 0 ? "" : ", ").append(typeText).append(' ').append(parameter.getSimpleName());
            }
            signature.append(')');
            if (!method.getThrownTypes().isEmpty()) {
                signature.append(" throws ").append(method.getThrownTypes().stream().map(t -> simple(t, false)).collect(Collectors.joining(", ")));
            }
            return signature.toString();
        }

        private void restOfExecutable(TypeElement owner, ExecutableElement method, DocCommentTree doc,
                List<? extends VariableElement> parameters, boolean constructor, Map<String, Object> out) {

            Map<String, String> paramDocs = paramDocs(doc, method);
            if (!parameters.isEmpty()) {
                List<Object> params = new ArrayList<>();
                for (VariableElement parameter : parameters) {
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("name", parameter.getSimpleName().toString());
                    p.put("type", simple(parameter.asType(), true));
                    p.put("doc", paramDocs.getOrDefault(parameter.getSimpleName().toString(), ""));
                    params.add(p);
                }
                out.put("params", params);
            }
            if (!constructor && method.getReturnType().getKind() != TypeKind.VOID) {
                Map<String, Object> returns = new LinkedHashMap<>();
                returns.put("type", simple(method.getReturnType(), true));
                String text = "";
                if (doc != null) {
                    for (DocTree tag : doc.getBlockTags()) {
                        if (tag instanceof ReturnTree r) {
                            text = markdown(r.getDescription(), method);
                        }
                    }
                }
                returns.put("doc", text);
                out.put("returns", returns);
            }
            if (doc != null) {
                List<Object> raises = new ArrayList<>();
                for (DocTree tag : doc.getBlockTags()) {
                    if (tag instanceof ThrowsTree t) {
                        Map<String, Object> raise = new LinkedHashMap<>();
                        Element target = resolve(t.getExceptionName(), method);
                        String typeName = target instanceof TypeElement te ? name(te) : t.getExceptionName().getSignature();
                        raise.put("type", typeName);
                        raise.put("doc", markdown(t.getDescription(), method));
                        raises.add(raise);
                    }
                }
                if (!raises.isEmpty()) {
                    out.put("raises", raises);
                }
            }
            if (!constructor) {
                String operation = config.operations().get(name(owner) + "." + method.getSimpleName());
                if (operation != null) {
                    out.put("operation", operation);
                }
            }
        }

        /** The doc of the method this one overrides, when this one has none of its own and the other is in the package. */
        private DocCommentTree inherited(TypeElement owner, ExecutableElement method) {
            for (TypeMirror up : types.directSupertypes(owner.asType())) {
                TypeElement superType = (TypeElement) ((DeclaredType) up).asElement();
                for (ExecutableElement candidate : ElementFilter.methodsIn(superType.getEnclosedElements())) {
                    if (elements.overrides(method, candidate, owner)) {
                        DocCommentTree doc = comment(candidate);
                        if (doc != null) {
                            return doc;
                        }
                    }
                }
                DocCommentTree further = inherited(superType, method);
                if (further != null) {
                    return further;
                }
            }
            return null;
        }

        private void deprecated(Element member, DocCommentTree comment, Map<String, Object> out) {
            String text = null;
            if (comment != null) {
                for (DocTree tag : comment.getBlockTags()) {
                    if (tag instanceof DeprecatedTree d) {
                        text = markdown(d.getBody(), member);
                    }
                }
            }
            if (text == null && elements.isDeprecated(member)) {
                text = "Deprecated.";
            }
            if (text != null) {
                out.put("deprecated", text.isEmpty() ? "Deprecated." : text);
            }
        }

        private Map<String, String> paramDocs(DocCommentTree comment, Element context) {
            Map<String, String> out = new HashMap<>();
            if (comment == null) {
                return out;
            }
            for (DocTree tag : comment.getBlockTags()) {
                if (tag instanceof ParamTree p && !p.isTypeParameter()) {
                    out.put(p.getName().getName().toString(), markdown(p.getDescription(), context));
                }
            }
            return out;
        }

        // ---------------------------------------------------------------- Markdown

        private Element resolve(ReferenceTree reference, Element context) {
            DocCommentTree comment = comment(context);
            if (comment == null) {
                return null;
            }
            DocTreePath path = DocTreePath.getPath(trees.getPath(context), comment, reference);
            return path == null ? null : trees.getElement(path);
        }

        /** Where a reference points in this file, or null when it points outside it. */
        private String anchorOf(Element target) {
            if (target == null) {
                return null;
            }
            String anchor = anchors.get(target);
            if (anchor == null && target instanceof ExecutableElement method) {
                // An accessor stands for its record component.
                for (Map.Entry<Element, String> entry : anchors.entrySet()) {
                    if (entry.getKey() instanceof RecordComponentElement component && method.equals(component.getAccessor())) {
                        return entry.getValue();
                    }
                }
            }
            return anchor;
        }

        private String linkLabel(ReferenceTree reference) {
            String signature = reference.getSignature();
            int paren = signature.indexOf('(');
            if (paren >= 0) {
                signature = signature.substring(0, paren);
            }
            if (signature.startsWith("#")) {
                return signature.substring(1);
            }
            signature = signature.replace('#', '.');
            // A qualified name reads as its simple name: java.util.UUID is UUID.
            Matcher qualified = Pattern.compile("^(?:[a-z]\\w*\\.)+").matcher(signature);
            return qualified.replaceFirst("");
        }

        String markdown(List<? extends DocTree> body, Element context) {
            Markdown out = new Markdown();
            render(body, context, out);
            return out.finish();
        }

        private void render(List<? extends DocTree> body, Element context, Markdown out) {
            for (DocTree node : body) {
                switch (node.getKind()) {
                    case TEXT -> out.text(((TextTree) node).getBody());
                    case ENTITY -> out.text(entity(((EntityTree) node).getName().toString()));
                    case CODE -> out.code(((LiteralTree) node).getBody().getBody());
                    case LITERAL -> out.text(((LiteralTree) node).getBody().getBody());
                    case LINK, LINK_PLAIN -> {
                        LinkTree link = (LinkTree) node;
                        String label = link.getLabel().isEmpty() ? linkLabel(link.getReference())
                                : Markdown.plain(link.getLabel());
                        String anchor = anchorOf(resolve(link.getReference(), context));
                        if (anchor == null) {
                            out.code(label);
                        } else {
                            out.link(label, anchor);
                        }
                    }
                    case VALUE -> {
                        ValueTree value = (ValueTree) node;
                        Element target = value.getReference() == null ? context : resolve(value.getReference(), context);
                        if (target instanceof VariableElement v && v.getConstantValue() != null) {
                            out.code(elements.getConstantExpression(v.getConstantValue()));
                        }
                    }
                    case START_ELEMENT -> {
                        StartElementTree start = (StartElementTree) node;
                        String tag = start.getName().toString().toLowerCase(Locale.ROOT);
                        String href = null;
                        for (DocTree attribute : start.getAttributes()) {
                            if (attribute instanceof AttributeTree a && a.getName().contentEquals("href") && a.getValue() != null) {
                                href = a.getValue().stream().map(DocTree::toString).collect(Collectors.joining());
                            }
                        }
                        out.open(tag, href);
                    }
                    case END_ELEMENT -> out.close(((EndElementTree) node).getName().toString().toLowerCase(Locale.ROOT));
                    default -> {
                        // {@inheritDoc}, {@index}, an unknown tag: nothing a reader needs.
                    }
                }
            }
        }

        private static String entity(String name) {
            return switch (name) {
                case "lt" -> "<";
                case "gt" -> ">";
                case "amp" -> "&";
                case "quot" -> "\"";
                case "apos" -> "'";
                case "nbsp" -> " ";
                default -> name.startsWith("#x") ? Character.toString(Integer.parseInt(name.substring(2), 16))
                        : name.startsWith("#") ? Character.toString(Integer.parseInt(name.substring(1))) : "";
            };
        }
    }

    /**
     * Markdown built from a doc comment's parts: text whose whitespace collapses, code spans, links,
     * paragraphs, list items, emphasis and preformatted blocks.
     */
    static final class Markdown {
        private static final char PARAGRAPH = '\u0001';
        private static final char ITEM = '\u0002';
        private final StringBuilder text = new StringBuilder();
        private int pre;
        private String href;
        private int linkStart = -1;

        void text(String body) {
            if (pre > 0) {
                text.append(body);
            } else {
                text.append(body.replaceAll("\\s+", " "));
            }
        }

        void code(String body) {
            if (pre > 0) {
                text.append(body);
                return;
            }
            String flat = body.replaceAll("\\s+", " ").strip();
            if (flat.isEmpty()) {
                return;
            }
            String fence = flat.contains("`") ? "``" : "`";
            String pad = flat.startsWith("`") || flat.endsWith("`") ? " " : "";
            text.append(fence).append(pad).append(flat).append(pad).append(fence);
        }

        void link(String label, String anchor) {
            text.append('[').append(label).append("](#").append(anchor).append(')');
        }

        void open(String tag, String link) {
            switch (tag) {
                case "p", "ul", "ol", "div", "dl", "table" -> text.append(PARAGRAPH);
                case "li", "dt", "dd" -> text.append(ITEM);
                case "b", "strong" -> text.append("**");
                case "i", "em", "cite", "var" -> text.append('*');
                case "code", "tt" -> text.append('`');
                case "br" -> text.append(' ');
                case "pre" -> {
                    text.append(PARAGRAPH).append("\u0003");
                    pre++;
                }
                case "a" -> {
                    href = link;
                    linkStart = text.length();
                }
                default -> {
                }
            }
        }

        void close(String tag) {
            switch (tag) {
                case "p", "ul", "ol", "div", "dl", "table" -> text.append(PARAGRAPH);
                case "b", "strong" -> text.append("**");
                case "i", "em", "cite", "var" -> text.append('*');
                case "code", "tt" -> text.append('`');
                case "pre" -> {
                    text.append("\u0004").append(PARAGRAPH);
                    pre = Math.max(0, pre - 1);
                }
                case "a" -> {
                    if (href != null && linkStart >= 0 && (href.startsWith("https://") || href.startsWith("http://"))) {
                        String label = text.substring(linkStart).strip();
                        text.setLength(linkStart);
                        text.append('[').append(label).append("](").append(href).append(')');
                    }
                    href = null;
                    linkStart = -1;
                }
                default -> {
                }
            }
        }

        static String plain(List<? extends DocTree> label) {
            Markdown inner = new Markdown();
            for (DocTree node : label) {
                if (node instanceof TextTree t) {
                    inner.text(t.getBody());
                }
            }
            return inner.finish();
        }

        /** The Markdown: paragraphs separated by one blank line, list items one per line, a code block fenced. */
        String finish() {
            String all = text.toString();
            List<String> out = new ArrayList<>();
            for (String block : Pattern.compile(String.valueOf(PARAGRAPH)).split(all, -1)) {
                if (block.startsWith("\u0003")) {
                    String code = block.substring(1).replace("\u0004", "");
                    code = code.replaceAll("^\\s*\\n", "").stripTrailing();
                    out.add("```java\n" + dedent(code) + "\n```");
                    continue;
                }
                String[] items = Pattern.compile(String.valueOf(ITEM)).split(block, -1);
                StringBuilder lines = new StringBuilder();
                String lead = items[0].strip().replaceAll(" {2,}", " ");
                if (!lead.isEmpty()) {
                    lines.append(lead);
                }
                for (int i = 1; i < items.length; i++) {
                    String item = items[i].strip().replaceAll(" {2,}", " ");
                    if (item.isEmpty()) {
                        continue;
                    }
                    if (lines.length() > 0) {
                        lines.append('\n');
                    }
                    lines.append("- ").append(item);
                }
                if (lines.length() > 0) {
                    out.add(lines.toString());
                }
            }
            return String.join("\n\n", out);
        }

        private static String dedent(String code) {
            List<String> lines = code.lines().toList();
            int indent = lines.stream().filter(l -> !l.isBlank()).mapToInt(l -> l.length() - l.stripLeading().length()).min().orElse(0);
            return lines.stream().map(l -> l.length() >= indent ? l.substring(indent) : l.strip()).collect(Collectors.joining("\n"));
        }
    }

    /** JSON as the format wants it: two-space indent, LF, UTF-8 text unescaped, keys in insertion order. */
    static final class Json {
        private Json() {
        }

        @SuppressWarnings("unchecked")
        static void write(Object value, StringBuilder out, int depth) {
            if (value instanceof Map<?, ?> map) {
                if (map.isEmpty()) {
                    out.append("{}");
                    return;
                }
                out.append("{\n");
                int i = 0;
                for (Map.Entry<String, Object> entry : ((Map<String, Object>) map).entrySet()) {
                    indent(out, depth + 1);
                    string(entry.getKey(), out);
                    out.append(": ");
                    write(entry.getValue(), out, depth + 1);
                    out.append(++i < map.size() ? ",\n" : "\n");
                }
                indent(out, depth);
                out.append('}');
            } else if (value instanceof List<?> list) {
                if (list.isEmpty()) {
                    out.append("[]");
                    return;
                }
                out.append("[\n");
                for (int i = 0; i < list.size(); i++) {
                    indent(out, depth + 1);
                    write(list.get(i), out, depth + 1);
                    out.append(i + 1 < list.size() ? ",\n" : "\n");
                }
                indent(out, depth);
                out.append(']');
            } else if (value instanceof String s) {
                string(s, out);
            } else if (value instanceof Number || value instanceof Boolean) {
                out.append(value);
            } else if (value == null) {
                out.append("null");
            } else {
                throw new IllegalArgumentException("not JSON: " + value.getClass());
            }
        }

        private static void indent(StringBuilder out, int depth) {
            out.append("  ".repeat(depth));
        }

        private static void string(String s, StringBuilder out) {
            out.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    case '\b' -> out.append("\\b");
                    case '\f' -> out.append("\\f");
                    default -> {
                        if (c < 0x20) {
                            out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                        } else {
                            out.append(c);
                        }
                    }
                }
            }
            out.append('"');
        }
    }
}

package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * The shared suite's fake MFX Hub, started once for the run. It is the suite's own Python program,
 * vendored in conformance/suite; it listens on a free localhost port and prints
 * {@code listening http://127.0.0.1:<port>/v1} when it is ready.
 *
 * <p>The interpreter is {@code MFX_SUITE_PYTHON} when set (CI points it at an environment installed from
 * hub-requirements.txt), otherwise {@code python3}, or {@code python} on Windows.
 */
final class Hub implements AutoCloseable {
    static final Path SUITE = Path.of("conformance", "suite");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Process process;
    private final HttpClient control = HttpClient.newHttpClient();
    private final URI baseUrl;
    private final String origin;

    Hub() throws IOException, InterruptedException {
        String python = System.getenv("MFX_SUITE_PYTHON");
        if (python == null || python.isEmpty()) {
            python = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "python" : "python3";
        }
        ProcessBuilder start = new ProcessBuilder(python, "-m", "mfx_fake_hub", "--cases", SUITE.resolve("cases").toString())
                .redirectError(ProcessBuilder.Redirect.INHERIT);
        Map<String, String> environment = start.environment();
        environment.put("PYTHONPATH", SUITE.toAbsolutePath().toString());
        environment.put("PYTHONDONTWRITEBYTECODE", "1");
        process = start.start();
        BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        String line = CompletableFuture.supplyAsync(() -> {
            try {
                return out.readLine();
            } catch (IOException failed) {
                throw new java.io.UncheckedIOException(failed);
            }
        }).completeOnTimeout(null, 20, TimeUnit.SECONDS).join();
        if (line == null || !line.startsWith("listening ")) {
            process.destroyForcibly();
            throw new IllegalStateException("the fake hub said '" + line + "', not where it listens");
        }
        baseUrl = URI.create(line.substring("listening ".length()).strip());
        origin = baseUrl.getScheme() + "://" + baseUrl.getRawAuthority();
    }

    /** The API base the hub serves, as a client is built with it. */
    URI baseUrl() {
        return baseUrl;
    }

    /** Loads a case; the hub answers it with its fixtures expanded. */
    JsonNode load(String id) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(origin + "/_hub/case"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("id", id))))
                .build();
        HttpResponse<String> answer = control.send(request, HttpResponse.BodyHandlers.ofString());
        if (answer.statusCode() != 200) {
            throw new IllegalStateException("the hub could not load " + id + ": " + answer.statusCode() + " " + answer.body());
        }
        return JSON.readTree(answer.body());
    }

    /** The hub's verdict on what was sent: the problems it saw, none when ok. */
    List<String> problems() throws IOException, InterruptedException {
        HttpResponse<String> answer = control.send(HttpRequest.newBuilder(URI.create(origin + "/_hub/verdict")).build(),
                HttpResponse.BodyHandlers.ofString());
        JsonNode verdict = JSON.readTree(answer.body());
        List<String> problems = new ArrayList<>();
        verdict.path("problems").forEach(problem -> problems.add(problem.toString()));
        if (!verdict.path("ok").asBoolean(false) && problems.isEmpty()) {
            problems.add("the verdict is not ok and names no problem");
        }
        return problems;
    }

    @Override
    public void close() {
        control.close();
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }
}

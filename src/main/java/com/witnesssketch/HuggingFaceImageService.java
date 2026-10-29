package com.witnesssketch;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HuggingFaceImageService {

    /** Total images in the original_sketch dataset */
    private static final int DATASET_SIZE = 1194;

    /** Spacing between the 3 selected variations to ensure visual diversity */
    private static final int VARIATION_STEP = DATASET_SIZE / 4;

    private static final Map<String, Set<String>> ALLOWED_FEATURES = Map.of(
            "faceShape",   Set.of("Oval", "Round", "Square", "Long", "Heart-shaped"),
            "hairstyle",   Set.of("Short", "Long", "Straight", "Wavy", "Curly", "Receding"),
            "eyebrows",    Set.of("Thin", "Medium", "Thick", "Arched"),
            "eyeShape",    Set.of("Small", "Medium", "Large", "Deep-set"),
            "noseShape",   Set.of("Narrow", "Straight", "Broad", "Rounded"),
            "facialHair",  Set.of("Clean-shaven", "Light moustache", "Thick moustache", "Short beard", "Full beard"),
            "jawline",     Set.of("Soft", "Rounded", "Angular", "Broad"),
            "expression",  Set.of("Neutral", "Slightly serious"));

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final String token;
    private final String endpoint;
    private final String model;

    /** Resolved path to the on-disk dataset directory */
    private final java.nio.file.Path datasetDir;

    public HuggingFaceImageService(
            @Value("${witnesssketch.huggingface.token:}") String token,
            @Value("${witnesssketch.huggingface.endpoint}") String endpoint,
            @Value("${witnesssketch.huggingface.model}") String model) {
        this.token    = token.strip();
        this.endpoint = endpoint.replaceAll("/+$", "");
        this.model    = model.strip();

        // Resolve the dataset directory (try the nested path first, then the static/sketches copy)
        java.nio.file.Path candidate1 = java.nio.file.Path.of(
                "C:/Users/USER/Downloads/original_sketch/original_sketch");
        java.nio.file.Path candidate2 = java.nio.file.Path.of(
                "C:/Users/USER/Downloads/original_sketch");
        // Also try the in-JAR static/sketches directory that Spring Boot exposes
        java.nio.file.Path candidate3 = resolveStaticSketchesDir();

        if (java.nio.file.Files.isDirectory(candidate1)) {
            this.datasetDir = candidate1;
        } else if (java.nio.file.Files.isDirectory(candidate2)) {
            this.datasetDir = candidate2;
        } else {
            this.datasetDir = candidate3;
        }
    }

    /**
     * Try to locate the sketches/ folder that was copied into src/main/resources/static/sketches
     * and surfaced on the file-system by Spring Boot during development (target/classes/static/sketches).
     */
    private static java.nio.file.Path resolveStaticSketchesDir() {
        // When running via spring-boot:run the working directory is the project root
        java.nio.file.Path dev = java.nio.file.Path.of("target/classes/static/sketches");
        if (java.nio.file.Files.isDirectory(dev)) return dev;
        java.nio.file.Path src = java.nio.file.Path.of("src/main/resources/static/sketches");
        if (java.nio.file.Files.isDirectory(src)) return src;
        return dev; // fallback, may not exist
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public GeneratedImage generate(Map<String, String> rawFeatures, String instructions) {
        // Validate known facial feature keys
        if (rawFeatures == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide facial feature selections.");
        }
        Map<String, String> features = new LinkedHashMap<>();
        ALLOWED_FEATURES.forEach((key, allowed) -> {
            String val = rawFeatures.get(key);
            if (val == null || !allowed.contains(val)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid facial feature: " + key + ".");
            }
            features.put(key, val);
        });

        String extra = instructions == null ? "" : instructions.strip();
        if (extra.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Additional instructions must be no more than 500 characters.");
        }

        // Use local dataset when no HF token is configured
        if (token.isBlank()) {
            return loadFromDataset(features, extra);
        }

        // ── HuggingFace API path ──────────────────────────────────────────────
        String prompt = "Fictional, non-identifiable graphite pencil portrait for illustration only; "
                + "front-facing head and shoulders, plain background, black-and-white hand-drawn sketch, "
                + "no text, no claim of identity. Facial attributes: " + features
                + (extra.isBlank() ? "" : ". Visual direction: " + extra);
        try {
            byte[] body = new tools.jackson.databind.ObjectMapper()
                    .writeValueAsBytes(Map.of("inputs", prompt));
            URI uri = URI.create(endpoint + "/" + model);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("Accept", "image/png, image/jpeg, image/webp, application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            String type = response.headers().firstValue("Content-Type").orElse("")
                    .split(";", 2)[0].strip().toLowerCase(java.util.Locale.ROOT);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "The configured image service did not accept the request (HTTP " + response.statusCode() + ").");
            }
            if (!Set.of("image/png", "image/jpeg", "image/webp").contains(type)
                    || response.body().length == 0 || response.body().length > 5 * 1024 * 1024) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "The image service did not return a supported image smaller than 5 MB.");
            }
            return new GeneratedImage(response.body(), type);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Image generation was interrupted.", e);
        } catch (IOException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not reach the image service.", e);
        }
    }

    // -------------------------------------------------------------------------
    // Dataset selection logic
    // -------------------------------------------------------------------------

    /**
     * Pick a deterministic sketch from the 1194-image dataset based on the
     * facial attribute combination + variation index encoded in the instructions.
     *
     * Strategy:
     *   1. Hash the feature map to get a "base index" (0–DATASET_SIZE-1).
     *   2. Detect which variation (1/2/3) is requested from the instructions.
     *   3. Return base + (variation-1) × VARIATION_STEP, wrapping around.
     *
     * This gives visually different sketches for each variation while staying
     * consistent when the same feature set is requested again.
     */
    private GeneratedImage loadFromDataset(Map<String, String> features, String instructions) {
        // Compute a stable hash from the feature map
        int hash = Math.abs(features.toString().hashCode());
        int base = hash % DATASET_SIZE; // 0-indexed

        // Detect variation number from instruction string
        int variationIndex = 0; // 0, 1, or 2
        if (instructions != null) {
            String lower = instructions.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("variation 1")) variationIndex = 0;
            else if (lower.contains("variation 2")) variationIndex = 1;
            else if (lower.contains("variation 3")) variationIndex = 2;
        }

        // Spread the three variations across the dataset
        int imageIndex = (base + variationIndex * VARIATION_STEP) % DATASET_SIZE;

        // File names are zero-padded 5-digit numbers starting at 00001
        String filename = String.format("%05d.jpg", imageIndex + 1);
        java.nio.file.Path file = datasetDir.resolve(filename);

        // Safety fallback
        if (!java.nio.file.Files.exists(file)) {
            file = datasetDir.resolve("00001.jpg");
        }

        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(file);
            return new GeneratedImage(bytes, "image/jpeg");
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to read dataset sketch '" + filename + "': " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Record
    // -------------------------------------------------------------------------

    public record GeneratedImage(byte[] bytes, String contentType) {}
}

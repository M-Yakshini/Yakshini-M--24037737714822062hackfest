package com.witnesssketch;

import java.sql.Timestamp;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/versions")
public class VersionController {
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final Map<String, Set<String>> ALLOWED_FEATURES = Map.of(
            "faceShape", Set.of("Oval", "Round", "Square", "Long", "Heart-shaped"),
            "hairstyle", Set.of("Short", "Long", "Straight", "Wavy", "Curly", "Receding"),
            "eyebrows", Set.of("Thin", "Medium", "Thick", "Arched"),
            "eyeShape", Set.of("Small", "Medium", "Large", "Deep-set"),
            "noseShape", Set.of("Narrow", "Straight", "Broad", "Rounded"),
            "facialHair", Set.of("Clean-shaven", "Light moustache", "Thick moustache", "Short beard", "Full beard"),
            "jawline", Set.of("Soft", "Rounded", "Angular", "Broad"),
            "expression", Set.of("Neutral", "Slightly serious"));
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public VersionController(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<SavedVersion> list(HttpServletRequest request) {
        long userId = SketchGenerationController.requireUser(request.getSession(false));
        int count = jdbc.queryForObject("SELECT COUNT(*) FROM sketch_version WHERE user_id = ?", Integer.class, userId);
        return jdbc.query(
                "SELECT id, summary, features_json, image_base64, mime_type, created_at "
                        + "FROM sketch_version WHERE user_id = ? ORDER BY created_at DESC, id DESC",
                (rs, row) -> {
                    try {
                        Timestamp created = rs.getTimestamp("created_at");
                        String featJson = rs.getString("features_json");
                        return new SavedVersion(
                                String.valueOf(rs.getLong("id")), count - row,
                                created.toInstant().toString(), rs.getString("summary"),
                                objectMapper.readValue(featJson, new TypeReference<Map<String, String>>() {}),
                                rs.getString("image_base64"), rs.getString("mime_type"),
                                featJson, created.toInstant().toString());
                    } catch (JacksonException exception) {
                        throw new IllegalStateException("Could not read saved sketch version.", exception);
                    }
                }, userId);
    }

    @PostMapping
    public SavedVersion save(@RequestBody SaveVersionRequest request, HttpServletRequest servletRequest) {
        long userId = SketchGenerationController.requireUser(servletRequest.getSession(false));
        if (request == null || request.summary() == null || request.summary().isBlank()
                || request.summary().length() > 2000) {
            throw badRequest("Provide a version summary of up to 2,000 characters.");
        }
        Map<String, String> features = validateFeatures(request.features());
        if (!Set.of("image/png", "image/jpeg", "image/webp").contains(request.mimeType())) {
            throw badRequest("Only PNG, JPEG, or WebP images can be saved.");
        }
        if (request.imageBase64() == null || request.imageBase64().length() > (MAX_IMAGE_BYTES * 4 / 3) + 4) {
            throw badRequest("The image must be smaller than 5 MB.");
        }
        try {
            byte[] image = Base64.getDecoder().decode(request.imageBase64());
            if (image.length == 0 || image.length > MAX_IMAGE_BYTES) throw badRequest("The image must be smaller than 5 MB.");
        } catch (IllegalArgumentException exception) {
            throw badRequest("The image data is not valid base64.");
        }
        String featuresJson;
        try {
            featuresJson = objectMapper.writeValueAsString(features);
        } catch (JacksonException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not prepare version metadata.", exception);
        }
        jdbc.update("INSERT INTO sketch_version (user_id, summary, features_json, image_base64, mime_type) "
                + "VALUES (?, ?, ?, ?, ?)", userId, request.summary().strip(),
                featuresJson, request.imageBase64(), request.mimeType());
        int versionNumber = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sketch_version WHERE user_id = ?", Integer.class, userId);
        return jdbc.queryForObject(
                "SELECT id, summary, features_json, image_base64, mime_type, created_at "
                        + "FROM sketch_version WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                (rs, row) -> {
                    try {
                        String featJson = rs.getString("features_json");
                        String createdAt = rs.getTimestamp("created_at").toInstant().toString();
                        return new SavedVersion(String.valueOf(rs.getLong("id")), versionNumber,
                                createdAt, rs.getString("summary"),
                                objectMapper.readValue(featJson, new TypeReference<Map<String, String>>() {}),
                                rs.getString("image_base64"), rs.getString("mime_type"),
                                featJson, createdAt);
                    } catch (JacksonException exception) {
                        throw new IllegalStateException("Could not read saved sketch version.", exception);
                    }
                }, userId);
    }

    private static Map<String, String> validateFeatures(Map<String, String> features) {
        if (features == null) throw badRequest("Provide facial feature selections.");
        // Validate only the known facial feature keys; allow extra metadata keys (caseNumber, caseDetails, etc.)
        ALLOWED_FEATURES.forEach((key, allowed) -> {
            String val = features.get(key);
            if (val == null || !allowed.contains(val)) throw badRequest("Invalid value for facial feature: " + key + ".");
        });
        return java.util.Collections.unmodifiableMap(features);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record SaveVersionRequest(String summary, Map<String, String> features, String imageBase64, String mimeType) {}
    public record SavedVersion(String id, int number, String date, String summary, Map<String, String> features,
            String imageBase64, String mimeType, String featuresJson, String createdAt) {}
}

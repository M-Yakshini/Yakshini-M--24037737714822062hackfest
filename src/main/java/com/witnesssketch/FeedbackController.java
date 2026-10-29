package com.witnesssketch;

import java.sql.Timestamp;
import java.util.List;
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

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {
    private static final Set<String> TYPES = Set.of("feedback", "suggestion", "accessibility", "bug", "accuracy", "feature", "general");
    private final JdbcTemplate jdbc;

    public FeedbackController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<FeedbackEntry> list(HttpServletRequest servletRequest) {
        long userId = SketchGenerationController.requireUser(servletRequest.getSession(false));
        return jdbc.query(
                "SELECT id, feedback_type, subject, message, created_at FROM user_feedback WHERE user_id = ? ORDER BY created_at DESC",
                (rs, row) -> new FeedbackEntry(
                        String.valueOf(rs.getLong("id")),
                        rs.getString("feedback_type"),
                        rs.getString("subject"),
                        rs.getString("message"),
                        rs.getTimestamp("created_at").toInstant().toString()),
                userId);
    }

    @PostMapping
    public FeedbackResponse submit(@RequestBody FeedbackRequest request, HttpServletRequest servletRequest) {
        long userId = SketchGenerationController.requireUser(servletRequest.getSession(false));
        if (request == null || !TYPES.contains(request.type())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid feedback category.");
        }
        String subject = request.subject() == null ? "" : request.subject().strip();
        String message = request.message() == null ? "" : request.message().strip();
        if (subject.isEmpty() || subject.length() > 100 || message.isEmpty() || message.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a title and feedback of up to 2,000 characters.");
        }
        jdbc.update("INSERT INTO user_feedback (user_id, feedback_type, subject, message) VALUES (?, ?, ?, ?)",
                userId, request.type(), subject, message);
        return new FeedbackResponse("Feedback received. Thank you for helping us improve the workspace.");
    }

    public record FeedbackRequest(String type, String subject, String message) {}
    public record FeedbackResponse(String message) {}
    public record FeedbackEntry(String id, String feedbackType, String subject, String message, String createdAt) {}
}

package com.witnesssketch;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    static final String CSRF_SESSION_KEY = AuthController.class.getName() + ".csrf";
    static final String USER_ID_SESSION_KEY = AuthController.class.getName() + ".userId";
    static final String USER_NAME_SESSION_KEY = AuthController.class.getName() + ".userName";
    static final String USER_EMAIL_SESSION_KEY = AuthController.class.getName() + ".userEmail";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private final String dummyHash = encoder.encode(randomToken());

    public AuthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        String token = (String) session.getAttribute(CSRF_SESSION_KEY);
        if (token == null) {
            token = randomToken();
            session.setAttribute(CSRF_SESSION_KEY, token);
        }
        return Map.of("csrfToken", token);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegistrationRequest request, HttpServletRequest servletRequest) {
        if (request == null) throw badRequest("Registration details are required.");
        String name = request.name() == null ? "" : request.name().strip();
        String email = normalizeEmail(request.email());
        String password = request.password();
        if (name.isEmpty() || name.length() > 100) throw badRequest("Enter a name between 1 and 100 characters.");
        if (email.length() > 254 || !EMAIL_PATTERN.matcher(email).matches()) {
            throw badRequest("Enter a valid email address.");
        }
        if (password == null || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw badRequest("Password must be at least 12 characters and no more than 72 UTF-8 bytes.");
        }
        try {
            jdbc.update("INSERT INTO app_user (full_name, email, password_hash) VALUES (?, ?, ?)",
                    name, email, encoder.encode(password));
        } catch (DuplicateKeyException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "An account with this email already exists."));
        }
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE email = ?", Long.class, email);
        invalidateAndAuthenticate(servletRequest, userId, name, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("user", new UserResponse(name, email)));
    }

    @PostMapping("/login")
    public Map<String, UserResponse> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        if (request == null) throw badRequest("Email and password are required.");
        String email = normalizeEmail(request.email());
        String password = request.password() == null ? "" : request.password();
        UserRecord user = jdbc.query(
                "SELECT id, full_name, email, password_hash FROM app_user WHERE email = ?",
                resultSet -> resultSet.next()
                        ? new UserRecord(resultSet.getLong("id"), resultSet.getString("full_name"),
                                resultSet.getString("email"), resultSet.getString("password_hash"))
                        : null,
                email);
        boolean matches = encoder.matches(password, user == null ? dummyHash : user.passwordHash());
        if (user == null || !matches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
        }
        invalidateAndAuthenticate(servletRequest, user.id(), user.name(), user.email());
        return Map.of("user", new UserResponse(user.name(), user.email()));
    }

    @GetMapping("/me")
    public Map<String, UserResponse> currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(USER_ID_SESSION_KEY) == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue.");
        }
        return Map.of("user", new UserResponse(
                (String) session.getAttribute(USER_NAME_SESSION_KEY),
                (String) session.getAttribute(USER_EMAIL_SESSION_KEY)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return ResponseEntity.noContent().build();
    }

    private static void invalidateAndAuthenticate(HttpServletRequest request, Long userId, String name, String email) {
        HttpSession previous = request.getSession(false);
        if (previous != null) previous.invalidate();
        HttpSession session = request.getSession(true);
        session.setAttribute(USER_ID_SESSION_KEY, userId);
        session.setAttribute(USER_NAME_SESSION_KEY, name);
        session.setAttribute(USER_EMAIL_SESSION_KEY, email);
        session.setAttribute(CSRF_SESSION_KEY, randomToken());
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    public record RegistrationRequest(String name, String email, String password) {}
    public record LoginRequest(String email, String password) {}
    public record UserResponse(String name, String email) {}
    private record UserRecord(Long id, String name, String email, String passwordHash) {}
}

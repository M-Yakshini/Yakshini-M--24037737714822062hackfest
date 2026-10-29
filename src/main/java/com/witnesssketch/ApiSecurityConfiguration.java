package com.witnesssketch;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Configuration
public class ApiSecurityConfiguration implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new CsrfProtectionInterceptor()).addPathPatterns("/api/**");
    }

    private static final class CsrfProtectionInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws Exception {
            String method = request.getMethod();
            if (HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method)
                    || HttpMethod.OPTIONS.matches(method)) {
                return true;
            }
            HttpSession session = request.getSession(false);
            String expected = session == null ? null
                    : (String) session.getAttribute(AuthController.CSRF_SESSION_KEY);
            String supplied = request.getHeader("X-CSRF-TOKEN");
            if (expected != null && supplied != null && MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"message\":\"CSRF token is missing or invalid. Refresh and try again.\"}");
            return false;
        }
    }
}

package com.example.store.filter;

import com.example.store.service.ApiKeyService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Validates incoming API keys using HMAC-SHA256 algorithmic verification.
 *
 * <p>Keys follow the format {@code sk_<clientId>_<timestamp>_<signature>}. The filter recomputes the HMAC signature and
 * compares it in constant time — no database lookup.
 *
 * <p>Requests from the configured CORS allowed origin bypass API key validation. Requests to /actuator/health/** are
 * always permitted without authentication.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyService apiKeyService;

    @Value("${app.security.cors.origin}")
    private String allowedOrigin;

    public ApiKeyAuthFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/") || path.startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Check Origin header — if it matches allowed origin, bypass API key check
        String origin = request.getHeader("Origin");
        if (allowedOrigin.equals(origin)) {
            setAuthentication("cors-origin");
            chain.doFilter(request, response);
            return;
        }

        // Extract X-API-Key header
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            writeUnauthorizedResponse(response, "Missing API key");
            return;
        }

        // Validate key algorithmically (HMAC-SHA256 signature verification)
        if (!apiKeyService.validateKey(apiKey)) {
            writeUnauthorizedResponse(response, "Invalid API key");
            return;
        }

        // Valid key — set authentication with client ID as principal
        String clientId = apiKeyService.extractClientId(apiKey);
        setAuthentication(clientId != null ? clientId : "api-key");
        chain.doFilter(request, response);
    }

    private void setAuthentication(String principal) {
        PreAuthenticatedAuthenticationToken authentication =
                new PreAuthenticatedAuthenticationToken(principal, null, AuthorityUtils.NO_AUTHORITIES);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void writeUnauthorizedResponse(HttpServletResponse response, String detail) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        String body = """
                {"type":"about:blank","title":"Unauthorized","status":401,"detail":"%s"}"""
                .formatted(detail);
        response.getWriter().write(body);
    }
}

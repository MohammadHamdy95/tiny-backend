package app.hamdy.shortener.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Bearer-token gate for the whole management API (/api/v1/links**);
 * redirects and /api/v1/health stay public. Empty token fails closed.
 */
@Component
public class AdminAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthFilter.class);
    private static final String BEARER = "Bearer ";

    private final byte[] token;

    public AdminAuthFilter(@Value("${shortener.admin-token:}") String adminToken) {
        this.token = adminToken.getBytes(StandardCharsets.UTF_8);
        if (adminToken.isEmpty()) {
            log.warn("SHORTENER_ADMIN_TOKEN is not set - admin endpoints will reject all requests");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/links");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (token.length > 0 && header != null && header.startsWith(BEARER)
                && MessageDigest.isEqual(token,
                        header.substring(BEARER.length()).getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"Unauthorized","status":401,\
                "detail":"Missing or invalid admin token"}""");
    }
}

package com.lezifx.trading.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lezifx.trading.infrastructure.context.TenantContext;
import com.lezifx.trading.service.auth.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
@Order(2)
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final List<String> SKIP_PATHS = List.of(
        "/api/v1/public/",
        "/api/v1/mpesa/c2b/callback",
        "/actuator/",
        "/ws/",
        "/api/v1/auth/login",
        "/api/v1/auth/register",
        "/api/v1/auth/refresh",
        "/api/v1/superadmin/auth/login",
        "/api/v1/superadmin/auth/refresh"
    );

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    @Autowired
    public JwtAuthFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        for (String skip : SKIP_PATHS) {
            if (path.startsWith(skip)) {
                chain.doFilter(request, response);
                return;
            }
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        if (!jwtService.isTokenValid(token)) {
            writeError(response, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "JWT token is invalid or expired");
            return;
        }

        String jwtTenantId = jwtService.extractTenantId(token);
        String role = jwtService.extractRole(token);

        java.util.UUID tenantContextId = TenantContext.getOrNull();
        if (tenantContextId != null && !"SUPER_ADMIN".equals(role)) {
            if (!tenantContextId.toString().equals(jwtTenantId)) {
                writeError(response, HttpStatus.UNAUTHORIZED, "TENANT_MISMATCH",
                    "JWT tenant does not match API key tenant");
                return;
            }
        }

        String userId = jwtService.extractUserId(token);

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            userId,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        authentication.setDetails(request);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        chain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, HttpStatus status,
                             String error, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of(
            "error", error,
            "message", message
        ));
    }
}
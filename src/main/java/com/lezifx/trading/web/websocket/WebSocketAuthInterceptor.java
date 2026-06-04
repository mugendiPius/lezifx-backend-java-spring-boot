package com.lezifx.trading.web.websocket;

import com.lezifx.trading.repository.TenantApiKeyRepository;
import com.lezifx.trading.service.auth.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final TenantApiKeyRepository tenantApiKeyRepository;

    @Autowired
    public WebSocketAuthInterceptor(JwtService jwtService,
                                     @Lazy TenantApiKeyRepository tenantApiKeyRepository) {
        this.jwtService = jwtService;
        this.tenantApiKeyRepository = tenantApiKeyRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor
            .getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        // Resolve API key → tenantId
        String apiKey = accessor.getFirstNativeHeader("X-API-Key");
        String resolvedTenantId = null;

        if (apiKey != null && !apiKey.isBlank()) {
            var tenantApiKey = tenantApiKeyRepository
                .findByApiKeyAndIsActiveTrue(apiKey)
                .orElse(null);
            if (tenantApiKey != null) {
                resolvedTenantId = tenantApiKey.getTenant().getId().toString();
            }
        }

        // Resolve JWT token
        String token = null;
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        if (token == null) {
            token = accessor.getFirstNativeHeader("token");
        }

        if (token != null && jwtService.isTokenValid(token)) {
            String userId = jwtService.extractUserId(token);
            String role = jwtService.extractRole(token);
            String jwtTenantId = jwtService.extractTenantId(token);

            if (resolvedTenantId != null && !"SUPER_ADMIN".equals(role)) {
                if (!resolvedTenantId.equals(jwtTenantId)) {
                    log.warn("WebSocket TENANT_MISMATCH: apiKey tenant={} jwt tenant={}",
                        resolvedTenantId, jwtTenantId);
                    throw new MessageDeliveryException(message, "TENANT_MISMATCH: JWT tenant does not match API key tenant");
                }
            }

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );
            accessor.setUser(auth);
            log.debug("WebSocket authenticated user={} tenant={}", userId, jwtTenantId);
        }

        return message;
    }
}
package com.banco.apigateway.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Valida el JWT emitido por {@code customer-service} en las rutas
 * protegidas del gateway (Fase 13, Parte III): rechaza con {@code 401} si
 * el header {@code Authorization: Bearer <token>} falta, tiene firma
 * invalida o esta expirado. Las rutas publicas (login y alta de cliente)
 * quedan exentas. Usa el mismo secreto HMAC que {@code customer-service}
 * (servido por config-server); el gateway solo verifica, nunca emite.
 */
@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final SecretKey key;

    public JwtAuthenticationFilter(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (isPublic(request)) {
            return chain.filter(exchange);
        }
        String token = extractToken(request);
        if (token == null || !isValid(token)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    /**
     * Rutas alcanzables sin token: login, alta de cliente, y todo
     * {@code yanki-service} completo, ya que el monedero Yanki
     * explicitamente "no requiere ser cliente del banco" (no tiene forma
     * de obtener un JWT emitido por customer-service).
     */
    private boolean isPublic(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();
        return (HttpMethod.POST.equals(method) && "/api/auth/login".equals(path))
                || (HttpMethod.POST.equals(method) && "/api/customers".equals(path))
                || path.startsWith("/api/yanki/");
    }

    private String extractToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring("Bearer ".length());
    }

    private boolean isValid(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException error) {
            log.warn("JWT invalido rechazado: {}", error.getMessage());
            return false;
        }
    }

    @Override
    public int getOrder() {
        return -1;
    }
}

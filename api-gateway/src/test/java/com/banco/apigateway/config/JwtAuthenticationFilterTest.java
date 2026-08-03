package com.banco.apigateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import reactor.core.publisher.Mono;

/**
 * Pruebas unitarias de {@link JwtAuthenticationFilter} (Fase 13): se
 * instancia el filtro directamente y se invoca {@code filter(...)} con un
 * {@link ServerWebExchange} de prueba, sin levantar contexto de Spring.
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-para-firmar-el-jwt-en-las-pruebas-unitarias-1234567890";

    private JwtAuthenticationFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(SECRET);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private String signedToken(String secret, long expiresInMillis) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("customer-1")
                .expiration(new Date(System.currentTimeMillis() + expiresInMillis))
                .signWith(key)
                .compact();
    }

    @Test
    void permiteElPasoConUnTokenValido() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/accounts").header("Authorization", "Bearer " + signedToken(SECRET, 60_000)));

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(exchange);
    }

    @Test
    void rechazaCuandoFaltaElToken() {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/accounts"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rechazaTokenExpirado() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/accounts").header("Authorization", "Bearer " + signedToken(SECRET, -60_000)));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rechazaTokenConFirmaInvalida() {
        String otroSecreto = "otro-secreto-completamente-distinto-para-la-prueba-abcdefghij";
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/accounts").header("Authorization", "Bearer " + signedToken(otroSecreto, 60_000)));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void permiteElLoginSinTokenPorSerRutaPublica() {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/auth/login"));

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(exchange);
    }

    @Test
    void permiteElAltaDeClienteSinTokenPorSerRutaPublica() {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/customers"));

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(exchange);
    }
}

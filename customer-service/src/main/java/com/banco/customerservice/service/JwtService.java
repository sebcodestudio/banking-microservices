package com.banco.customerservice.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.banco.customerservice.model.Customer;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Emite los JWT del login (Fase 13, Parte III). El secreto de firma
 * (HMAC-SHA) se sirve desde config-server ({@code jwt.secret}) y es el
 * mismo que usa {@code api-gateway} para validarlos; ningun otro
 * microservicio necesita conocerlo, ya que la validacion ocurre una sola
 * vez en el gateway.
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final long expirationSeconds;

    public JwtService(@Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-seconds:3600}") long expirationSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    /** Genera un token cuyo subject es el id del cliente, con su tipo y perfil como claims adicionales. */
    public TokenResponse generateToken(Customer customer) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expirationSeconds);
        String token = Jwts.builder()
                .subject(customer.getId())
                .claim("customerType", customer.getCustomerType().name())
                .claim("profile", customer.getProfile().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
        return new TokenResponse(token, "Bearer", expirationSeconds);
    }
}

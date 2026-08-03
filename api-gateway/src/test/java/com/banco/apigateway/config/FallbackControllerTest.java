package com.banco.apigateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifica que el endpoint de fallback responda 503 con un mensaje
 * legible cuando se invoca directamente (simulando un circuit breaker
 * abierto).
 *
 * <p>Se instancia el controlador directamente con
 * {@link WebTestClient#bindToController(Object...)} en vez de usar el
 * slice test {@code @WebFluxTest} (no disponible en Spring Boot 4.x):
 * {@link FallbackController} no tiene dependencias, por lo que no hace
 * falta levantar contexto de Spring para probarlo de forma aislada.</p>
 */
class FallbackControllerTest {

    private final WebTestClient webTestClient = WebTestClient.bindToController(new FallbackController()).build();

    @Test
    void fallbackDevuelveServiceUnavailableConMensaje() {
        webTestClient.get().uri("/fallback/customer-service")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("customer-service"));
    }
}

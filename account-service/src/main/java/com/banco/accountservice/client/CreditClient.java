package com.banco.accountservice.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

/**
 * Cliente REST reactivo hacia credit-service, usado (Fase 8) para
 * validar que un cliente ya tenga una tarjeta de credito activa con el
 * banco, requisito de los perfiles VIP (cuenta de ahorro) y PYME
 * (cuenta corriente) antes de abrir esa cuenta.
 *
 * <p>La URI base usa el esquema "lb://" para que el WebClient
 * (marcado con @LoadBalanced en {@link com.banco.accountservice.config.WebClientConfig})
 * resuelva la instancia real de credit-service a traves de Eureka. La
 * llamada esta protegida con un circuit breaker de Resilience4j
 * ("creditServiceCB", ver configuracion externalizada en config-server)
 * con un timeout de 2 segundos.</p>
 */
@Component
public class CreditClient {

    private final WebClient webClient;
    private final ReactiveCircuitBreakerFactory circuitBreakerFactory;

    public CreditClient(WebClient.Builder webClientBuilder,
            ReactiveCircuitBreakerFactory circuitBreakerFactory,
            @Value("${credit-service.base-url}") String creditServiceBaseUrl) {
        this.webClient = webClientBuilder.baseUrl(creditServiceBaseUrl).build();
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    /**
     * Indica si el cliente ya tiene una tarjeta de credito activa con el
     * banco. Si credit-service no responde dentro del timeout
     * configurado (2s) o el circuit breaker esta abierto, falla con 503
     * en vez de propagar un timeout crudo.
     */
    public Mono<Boolean> hasActiveCreditCard(String customerId) {
        ReactiveCircuitBreaker circuitBreaker = circuitBreakerFactory.create("creditServiceCB");

        Mono<Boolean> call = webClient.get()
                .uri("/api/credits/customers/{customerId}/has-credit-card", customerId)
                .retrieve()
                .bodyToMono(Boolean.class);

        return circuitBreaker.run(call, throwable -> Mono.error(new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "credit-service no disponible o excedio el timeout de 2s: " + throwable.getMessage())));
    }

    /**
     * Indica si el cliente tiene deuda vencida en algun producto de
     * credito (Fase 10), usado para rechazar la apertura de una cuenta
     * nueva a ese cliente. Mismo circuit breaker y timeout de 2s que
     * {@link #hasActiveCreditCard(String)}.
     */
    public Mono<Boolean> hasOverdueDebt(String customerId) {
        ReactiveCircuitBreaker circuitBreaker = circuitBreakerFactory.create("creditServiceCB");

        Mono<Boolean> call = webClient.get()
                .uri("/api/credits/customers/{customerId}/has-overdue-debt", customerId)
                .retrieve()
                .bodyToMono(Boolean.class);

        return circuitBreaker.run(call, throwable -> Mono.error(new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "credit-service no disponible o excedio el timeout de 2s: " + throwable.getMessage())));
    }
}

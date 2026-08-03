package com.banco.apigateway.config;

import java.time.Duration;

import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;

/**
 * Define las rutas del API Gateway hacia los 3 microservicios de
 * negocio, resolviendo cada instancia a traves de Eureka
 * ({@code lb://<nombre-de-la-app>}), y aplica un circuit breaker de
 * Resilience4j con timeout de 2 segundos en cada una. Si el
 * microservicio destino no responde a tiempo o el circuito esta
 * abierto, la peticion se redirige al {@link FallbackController}.
 */
@Configuration
public class GatewayConfig {

    private static final Duration CIRCUIT_BREAKER_TIMEOUT = Duration.ofSeconds(2);

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("customer-service", r -> r.path("/api/customers/**")
                        .filters(f -> f.circuitBreaker(c -> c
                                .setName("customerServiceCB")
                                .setFallbackUri("forward:/fallback/customer-service")))
                        .uri("lb://customer-service"))
                .route("account-service", r -> r.path("/api/accounts/**")
                        .filters(f -> f.circuitBreaker(c -> c
                                .setName("accountServiceCB")
                                .setFallbackUri("forward:/fallback/account-service")))
                        .uri("lb://account-service"))
                .route("debit-card-service", r -> r.path("/api/debit-cards/**")
                        .filters(f -> f.circuitBreaker(c -> c
                                .setName("accountServiceCB")
                                .setFallbackUri("forward:/fallback/account-service")))
                        .uri("lb://account-service"))
                .route("credit-service", r -> r.path("/api/credits/**")
                        .filters(f -> f.circuitBreaker(c -> c
                                .setName("creditServiceCB")
                                .setFallbackUri("forward:/fallback/credit-service")))
                        .uri("lb://credit-service"))
                .build();
    }

    /** Timeout uniforme de 2 segundos para las 3 rutas, con umbrales de apertura razonables. */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCircuitBreakerConfig() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        .slidingWindowSize(10)
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(CIRCUIT_BREAKER_TIMEOUT)
                        .build())
                .build());
    }
}

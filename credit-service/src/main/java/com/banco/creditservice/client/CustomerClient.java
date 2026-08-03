package com.banco.creditservice.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

/**
 * Cliente REST reactivo hacia customer-service, usado para validar el
 * tipo de cliente (personal / empresarial) antes de otorgar un credito.
 *
 * <p>La URI base usa el esquema "lb://" para que el WebClient
 * (marcado con @LoadBalanced en {@link com.banco.creditservice.config.WebClientConfig})
 * resuelva la instancia real de customer-service a traves de Eureka.
 * La llamada esta protegida con un circuit breaker de Resilience4j
 * ("customerServiceCB", ver configuracion externalizada en config-server)
 * con un timeout de 2 segundos: si customer-service no responde a tiempo
 * o el circuito esta abierto, se degrada a un error controlado en vez de
 * dejar la peticion colgada indefinidamente.</p>
 */
@Component
public class CustomerClient {

    private final WebClient webClient;
    private final ReactiveCircuitBreakerFactory circuitBreakerFactory;

    public CustomerClient(WebClient.Builder webClientBuilder,
            ReactiveCircuitBreakerFactory circuitBreakerFactory,
            @Value("${customer-service.base-url}") String customerServiceBaseUrl) {
        this.webClient = webClientBuilder.baseUrl(customerServiceBaseUrl).build();
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    /**
     * Obtiene un cliente por id, o falla con 400 si customer-service no lo
     * tiene registrado. Si customer-service no responde dentro del
     * timeout configurado (2s) o el circuit breaker esta abierto, falla
     * con 503 en vez de propagar un timeout crudo.
     */
    public Mono<CustomerDto> findCustomerById(String customerId) {
        ReactiveCircuitBreaker circuitBreaker = circuitBreakerFactory.create("customerServiceCB");

        Mono<CustomerDto> call = webClient.get()
                .uri("/api/customers/{id}", customerId)
                .retrieve()
                .onStatus(status -> status.value() == 404, response -> Mono.error(
                        new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Customer not found with id " + customerId)))
                .bodyToMono(CustomerDto.class);

        return circuitBreaker.run(call, throwable -> {
            if (throwable instanceof ResponseStatusException responseStatusException) {
                return Mono.error(responseStatusException);
            }
            return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "customer-service no disponible o excedio el timeout de 2s: " + throwable.getMessage()));
        });
    }
}

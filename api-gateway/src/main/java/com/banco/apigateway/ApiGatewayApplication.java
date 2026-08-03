package com.banco.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Punto de entrada de api-gateway: punto de entrada REST unico del
 * sistema bancario. Enruta las peticiones hacia customer-service,
 * account-service y credit-service resolviendo sus instancias via
 * Eureka (balanceo de carga con "lb://"), y aplica circuit breaker
 * (Resilience4j) con timeout de 2 segundos en cada ruta.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

}

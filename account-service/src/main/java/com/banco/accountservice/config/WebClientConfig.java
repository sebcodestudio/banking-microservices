package com.banco.accountservice.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Expone el WebClient.Builder que necesita CustomerClient para llamar a
 * customer-service. Al marcarlo con @LoadBalanced, las URIs con esquema
 * "lb://" (por ejemplo "lb://customer-service") se resuelven contra el
 * registro de Eureka en vez de apuntar a un host fijo.
 */
@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}

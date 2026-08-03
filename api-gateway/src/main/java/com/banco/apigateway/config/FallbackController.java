package com.banco.apigateway.config;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

/**
 * Respuesta de respaldo cuando el circuit breaker de una ruta esta abierto
 * o la llamada al microservicio destino excede el timeout de 2 segundos
 * configurado con Resilience4j. Evita que el gateway propague un error
 * de conexion crudo al cliente.
 */
@RestController
public class FallbackController {

    /** Respuesta generica de respaldo para cualquier microservicio no disponible. */
    @GetMapping("/fallback/{service}")
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Mono<String> fallback(@PathVariable String service) {
        return Mono.just("El servicio '" + service
                + "' no esta disponible en este momento (circuit breaker abierto o timeout superado). Intente nuevamente en unos segundos.");
    }
}

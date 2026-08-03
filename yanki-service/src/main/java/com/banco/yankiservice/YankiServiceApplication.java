package com.banco.yankiservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Punto de entrada de yanki-service: monedero movil Yanki (Fase 12,
 * Parte III). No requiere que el titular sea cliente del banco. Toda
 * interaccion con account-service (asociar tarjeta de debito, cargar o
 * retirar saldo hacia la cuenta principal) se hace via Kafka, nunca REST.
 * Habilita la auditoria reactiva de MongoDB, el listener de Kafka y se
 * registra como cliente de Eureka para ser enrutado desde el API Gateway.
 */
@SpringBootApplication
@EnableReactiveMongoAuditing
@EnableDiscoveryClient
@EnableKafka
public class YankiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(YankiServiceApplication.class, args);
    }

}

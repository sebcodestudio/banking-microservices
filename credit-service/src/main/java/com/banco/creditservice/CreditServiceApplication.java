package com.banco.creditservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing;

/**
 * Punto de entrada de credit-service: administra los productos de
 * credito (personal, empresarial y tarjeta de credito) y sus
 * movimientos. Habilita la auditoria reactiva de MongoDB para completar
 * automaticamente createdAt/updatedAt, y se registra como cliente de
 * Eureka para ser descubierto por el API Gateway y por customer-service.
 */
@SpringBootApplication
@EnableReactiveMongoAuditing
@EnableDiscoveryClient
public class CreditServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CreditServiceApplication.class, args);
	}

}

package com.banco.customerservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing;

/**
 * Punto de entrada de customer-service: administra la informacion de los
 * clientes del banco (personales y empresariales). Habilita la auditoria
 * reactiva de MongoDB para completar automaticamente createdAt/updatedAt,
 * y se registra como cliente de Eureka para ser descubierto por el
 * API Gateway y por los demas microservicios.
 */
@SpringBootApplication
@EnableReactiveMongoAuditing
@EnableDiscoveryClient
public class CustomerServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CustomerServiceApplication.class, args);
	}

}

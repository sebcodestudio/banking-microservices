package com.banco.accountservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Punto de entrada de account-service: administra las cuentas bancarias
 * (ahorro, corriente, plazo fijo) y sus movimientos. Habilita la
 * auditoria reactiva de MongoDB para completar automaticamente
 * createdAt/updatedAt, se registra como cliente de Eureka para ser
 * descubierto por el API Gateway y por credit-service/customer-service, y
 * habilita el listener de Kafka (Fase 12) que resuelve las solicitudes de
 * yanki-service.
 */
@SpringBootApplication
@EnableReactiveMongoAuditing
@EnableDiscoveryClient
@EnableKafka
public class AccountServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AccountServiceApplication.class, args);
	}

}

package com.banco.creditservice.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.banco.creditservice.model.CreditProduct;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo general de productos de credito (sobre la
 * coleccion "credits", compartida por los tres tipos de credito).
 */
public interface CreditProductRepository extends ReactiveMongoRepository<CreditProduct, String> {

    /** Todos los creditos de un cliente, sin importar el tipo. */
    Flux<CreditProduct> findByCustomerId(String customerId);

    /** Indica si el cliente tiene algun credito con balance pendiente y la proxima cuota ya vencida (Fase 10). */
    Mono<Boolean> existsByCustomerIdAndBalanceGreaterThanAndNextPaymentDueDateBefore(
            String customerId, BigDecimal balance, LocalDate date);
}

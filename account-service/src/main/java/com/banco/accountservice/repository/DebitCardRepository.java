package com.banco.accountservice.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.banco.accountservice.model.DebitCard;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo de tarjetas de debito (Fase 11).
 * Solo usa metodos derivados de Spring Data, sin @Query.
 */
public interface DebitCardRepository extends ReactiveMongoRepository<DebitCard, String> {

    /** Usado para limitar a una tarjeta de debito activa por cuenta. */
    Mono<Boolean> existsByAccountId(String accountId);

    /** Todas las tarjetas de debito de un cliente. */
    Flux<DebitCard> findByCustomerId(String customerId);
}

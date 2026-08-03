package com.banco.yankiservice.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.banco.yankiservice.model.YankiWallet;

import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo de monederos Yanki (Fase 12).
 * Solo usa metodos derivados de Spring Data, sin @Query.
 */
public interface YankiWalletRepository extends ReactiveMongoRepository<YankiWallet, String> {

    /** Usado para enviar/recibir pagos por numero de celular. */
    Mono<YankiWallet> findByPhoneNumber(String phoneNumber);

    Mono<Boolean> existsByDocumentNumber(String documentNumber);

    Mono<Boolean> existsByPhoneNumber(String phoneNumber);
}

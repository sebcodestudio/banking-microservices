package com.banco.creditservice.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.banco.creditservice.model.CreditCard;
import com.banco.creditservice.model.CreditStatus;

import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo de tarjetas de credito. Spring Data filtra
 * automaticamente por el discriminador de tipo (_class) de esta subclase.
 */
public interface CreditCardRepository extends ReactiveMongoRepository<CreditCard, String> {

    /**
     * Usado por account-service (Fase 8) para validar el requisito de
     * "tarjeta de credito previa con el banco" al abrir una cuenta con
     * perfil VIP o PYME.
     */
    Mono<Boolean> existsByCustomerIdAndStatus(String customerId, CreditStatus status);
}
